package com.vlm.quality.stage.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageTimings;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class StageTimingIntakeTest {

  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-029 R-21] stage.timing을 받으면 기록마다 저장하고 버전·상태를 남긴다")
  void storesEachTiming() {
    UUID source = UUID.randomUUID();
    UUID timing = UUID.randomUUID();
    Instant now = Instant.now();
    StageTimings batch =
        StageTimings.batch()
            .timing(
                timing,
                "analyzer.judge",
                "event-analyzer.handle/analyzer.judge",
                source,
                now,
                1200.5,
                "error",
                Map.of("modelId", "gpt-6.1-terra"))
            .timing(source, "event-analyzer.handle", now, 3000);

    send(batch);

    await().atMost(WAIT).until(() -> count(source) == 2);
    Map<String, Object> row =
        jdbc.queryForMap("SELECT * FROM stage_timings WHERE timing_id = ?", timing);
    assertThat(row.get("status")).isEqualTo("error");
    assertThat(row.get("duration_ms")).isEqualTo(1200.5);
    assertThat(row.get("service")).isEqualTo("event-analyzer");
    assertThat(row.get("versions").toString()).contains("gpt-6.1-terra");
  }

  @Test
  @DisplayName("[SPEC-029 R-21] 같은 timingId를 다시 받으면 마지막 값 하나만 남는다")
  void upsertsByTimingId() {
    UUID source = UUID.randomUUID();
    UUID timing = UUID.randomUUID();
    Instant first = Instant.now().minusSeconds(60);
    send(
        StageTimings.batch()
            .timing(
                timing,
                "analyzer.judge",
                "event-analyzer.handle/analyzer.judge",
                source,
                first,
                100,
                "ok",
                Map.of()));
    await().atMost(WAIT).until(() -> count(source) == 1);

    send(
        StageTimings.batch()
            .timing(
                timing,
                "analyzer.judge",
                "event-analyzer.handle/analyzer.judge",
                source,
                first.plusSeconds(5),
                250,
                "ok",
                Map.of()));

    await()
        .atMost(WAIT)
        .until(
            () ->
                Double.valueOf(250)
                    .equals(
                        jdbc.queryForObject(
                            "SELECT max(duration_ms) FROM stage_timings WHERE timing_id = ?",
                            Double.class,
                            timing)));
    assertThat(count(source)).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-029 R-21] 보존 기간(90일)보다 오래된 기록은 건너뛴다")
  void skipsExpiredTimings() {
    UUID source = UUID.randomUUID();
    send(
        StageTimings.batch()
            .timing(source, "analyzer.judge", Instant.now().minus(Duration.ofDays(91)), 100)
            .timing(source, "analyzer.escalate", Instant.now(), 100));

    await().atMost(WAIT).until(() -> count(source) == 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT stage_id FROM stage_timings WHERE source_id = ?", String.class, source))
        .isEqualTo("analyzer.escalate");
  }

  private void send(StageTimings batch) {
    kafka.send("stage.timing", batch.key(), batch.json()).join();
  }

  private int count(UUID source) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM stage_timings WHERE source_id = ?", Integer.class, source);
    return count == null ? 0 : count;
  }
}
