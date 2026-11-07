package com.vlm.quality.review.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.common.id.NameBasedUuid;
import com.vlm.quality.support.Events;
import com.vlm.quality.support.IntegrationTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class EventAnalyzedIntakeTest {

  private static final UUID NAMESPACE = UUID.fromString("5f0c1a3e-2b7d-5e49-9a61-0c7f3d2b8e41");
  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-029 R-1] reviewRequired인 판정을 받으면 itemId=uuid5(eventId)인 UNASSIGNED 항목을 만든다")
  void createsReviewRequiredItem() {
    UUID eventId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();

    send(Events.reviewRequired(eventId, sourceId));

    Map<String, Object> row = awaitItem(eventId);
    assertThat(row.get("item_id")).isEqualTo(NameBasedUuid.v5(NAMESPACE, eventId.toString()));
    assertThat(row.get("reason")).isEqualTo("review_required");
    assertThat(row.get("status")).isEqualTo("UNASSIGNED");
    assertThat(row.get("event_type")).isEqualTo("fall");
  }

  @Test
  @DisplayName("[SPEC-029 R-3] 같은 eventId를 내용을 바꿔 다시 받아도 항목은 하나이고 처음 값 그대로다")
  void keepsFirstItemForSameEvent() {
    UUID eventId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    send(Events.reviewRequired(eventId, sourceId));
    awaitItem(eventId);
    jdbc.update(
        "UPDATE review_items SET status = 'ASSIGNED', assignee_id = ? WHERE event_id = ?",
        UUID.randomUUID(),
        eventId);

    ObjectNode changed = Events.reviewRequired(eventId, sourceId);
    changed.put("candidateEventType", "ppe_missing");
    send(changed);
    UUID marker = UUID.randomUUID();
    send(Events.reviewRequired(marker, sourceId));
    awaitItem(marker);

    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM review_items WHERE event_id = ?", eventId);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM review_items WHERE event_id = ?", Integer.class, eventId))
        .isEqualTo(1);
    assertThat(row.get("event_type")).isEqualTo("fall");
    assertThat(row.get("status")).isEqualTo("ASSIGNED");
  }

  @Test
  @DisplayName("[SPEC-029 R-2] 판정이 unverified이고 사유가 없으면 표본 대상이 아니라 항목을 만들지 않는다")
  void skipsUnverifiedWithoutReason() {
    UUID eventId = UUID.randomUUID();
    UUID sourceId = UUID.randomUUID();
    ObjectNode event = Events.analyzed(eventId, sourceId);
    ((ObjectNode) event.get("analysis")).put("verificationStatus", "unknown");
    send(event);
    UUID marker = UUID.randomUUID();
    send(Events.reviewRequired(marker, sourceId));
    awaitItem(marker);

    assertThat(count(eventId)).isZero();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"budget_exceeded", "call_cap_reached", "model_unavailable"})
  @DisplayName("[SPEC-029 R-27] unverified 판정은 unverifiedReason을 사유로 큐에 넣는다")
  void queuesUnverifiedByReason(String reason) {
    UUID eventId = UUID.randomUUID();

    send(Events.unverified(eventId, UUID.randomUUID(), reason));

    assertThat(awaitItem(eventId).get("reason")).isEqualTo(reason);
  }

  @Test
  @DisplayName("[SPEC-029 R-1] 영상 시각이 24.8일(int32 ms)을 넘어도 건너뛰지 않고 항목을 만든다")
  void keepsVideoTimeBeyondInt32() {
    UUID eventId = UUID.randomUUID();
    ObjectNode event = Events.reviewRequired(eventId, UUID.randomUUID());
    long start = Integer.MAX_VALUE + 1_000L;
    event.put("startMs", start);
    event.put("endMs", start + 4_000);

    send(event);

    Map<String, Object> row = awaitItem(eventId);
    assertThat(row.get("start_ms")).isEqualTo(start);
    assertThat(row.get("end_ms")).isEqualTo(start + 4_000);
  }

  private void send(ObjectNode event) {
    kafka.send("event.analyzed", event.get("sourceId").asText(), event.toString()).join();
  }

  private Map<String, Object> awaitItem(UUID eventId) {
    await().atMost(WAIT).until(() -> count(eventId) == 1);
    return jdbc.queryForMap("SELECT * FROM review_items WHERE event_id = ?", eventId);
  }

  private int count(UUID eventId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_items WHERE event_id = ?", Integer.class, eventId);
    return count == null ? 0 : count;
  }
}
