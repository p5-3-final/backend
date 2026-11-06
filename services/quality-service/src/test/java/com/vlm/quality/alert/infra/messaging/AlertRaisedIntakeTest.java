package com.vlm.quality.alert.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.support.Events;
import com.vlm.quality.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class AlertRaisedIntakeTest {

  private static final Duration WAIT = Duration.ofSeconds(30);
  private static final Instant T0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;

  private final UUID source = UUID.randomUUID();
  private final UUID camera = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-029 R-28] pending 다음 confirmed를 받으면 후보별로 발행 시각과 판정 시각을 남긴다")
  void recordsPendingThenConfirmed() {
    UUID candidate = UUID.randomUUID();
    send(candidate, "pending", T0);
    send(candidate, "confirmed", T0.plusSeconds(4));

    awaitStatus(candidate, "confirmed");
    Map<String, Object> row = row(candidate);
    assertThat(instant(row, "pending_at")).isEqualTo(T0);
    assertThat(instant(row, "resolved_at")).isEqualTo(T0.plusSeconds(4));
  }

  @Test
  @DisplayName("[SPEC-029 R-28] confirmed가 먼저 오고 pending이 늦게 와도 두 시각을 모두 남기고 상태는 confirmed다")
  void handlesOutOfOrder() {
    UUID candidate = UUID.randomUUID();
    send(candidate, "confirmed", T0.plusSeconds(3));
    send(candidate, "pending", T0);

    await().atMost(WAIT).until(() -> pendingAt(candidate) != null);
    Map<String, Object> row = row(candidate);
    assertThat(row.get("status")).isEqualTo("confirmed");
    assertThat(instant(row, "pending_at")).isEqualTo(T0);
    assertThat(instant(row, "resolved_at")).isEqualTo(T0.plusSeconds(3));
  }

  @Test
  @DisplayName("[SPEC-029 R-28] 같은 상태를 다시 받으면 처음 시각을 유지한다")
  void ignoresRepeatedStatus() {
    UUID candidate = UUID.randomUUID();
    send(candidate, "pending", T0);
    send(candidate, "pending", T0.plusSeconds(9));
    send(candidate, "dismissed", T0.plusSeconds(2));
    send(candidate, "confirmed", T0.plusSeconds(5));
    UUID marker = UUID.randomUUID();
    send(marker, "pending", T0);

    await().atMost(WAIT).until(() -> pendingAt(marker) != null);
    Map<String, Object> row = row(candidate);
    assertThat(instant(row, "pending_at")).isEqualTo(T0);
    assertThat(row.get("status")).isEqualTo("dismissed");
    assertThat(instant(row, "resolved_at")).isEqualTo(T0.plusSeconds(2));
  }

  @Test
  @DisplayName("[SPEC-029 R-28] dismissed를 받으면 사유 alert_dismissed 검토 항목을 하나 만든다")
  void queuesDismissedForReview() {
    UUID candidate = UUID.randomUUID();
    ObjectNode dismissed = Events.alertRaised(candidate, source, camera, "dismissed", T0);
    kafka.send("alert.raised", source.toString(), dismissed.toString()).join();
    kafka.send("alert.raised", source.toString(), dismissed.toString()).join();

    String eventId = dismissed.get("eventId").asText();
    await()
        .atMost(WAIT)
        .until(
            () ->
                count("SELECT count(*) FROM review_items WHERE event_id = CAST(? AS UUID)", eventId)
                    == 1);
    Map<String, Object> item =
        jdbc.queryForMap(
            "SELECT reason, start_ms, review_reasons::text AS reasons FROM review_items"
                + " WHERE event_id = CAST(? AS UUID)",
            eventId);
    assertThat(item.get("reason")).isEqualTo("alert_dismissed");
    assertThat(item.get("start_ms")).isEqualTo(dismissed.get("observedMs").asLong());
    assertThat(item.get("reasons")).isEqualTo("[\"rejected\"]");
    assertThat(count("SELECT count(*) FROM review_items")).isEqualTo(1);
  }

  private void send(UUID candidate, String status, Instant at) {
    kafka
        .send(
            "alert.raised",
            source.toString(),
            Events.alertRaised(candidate, source, camera, status, at).toString())
        .join();
  }

  private void awaitStatus(UUID candidate, String status) {
    await()
        .atMost(WAIT)
        .until(
            () ->
                count(
                        "SELECT count(*) FROM alert_events WHERE candidate_id = ? AND status = ?",
                        candidate,
                        status)
                    == 1);
  }

  private Map<String, Object> row(UUID candidate) {
    return jdbc.queryForMap("SELECT * FROM alert_events WHERE candidate_id = ?", candidate);
  }

  private Instant pendingAt(UUID candidate) {
    return jdbc
        .queryForList(
            "SELECT pending_at FROM alert_events WHERE candidate_id = ?",
            Timestamp.class,
            candidate)
        .stream()
        .filter(java.util.Objects::nonNull)
        .map(Timestamp::toInstant)
        .findFirst()
        .orElse(null);
  }

  private static Instant instant(Map<String, Object> row, String column) {
    return ((Timestamp) row.get(column)).toInstant();
  }

  private int count(String sql, Object... args) {
    Integer count = jdbc.queryForObject(sql, Integer.class, args);
    return count == null ? 0 : count;
  }
}
