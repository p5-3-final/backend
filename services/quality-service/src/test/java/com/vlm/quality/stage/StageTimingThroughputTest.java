package com.vlm.quality.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageTimings;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class StageTimingThroughputTest {

  // NFR-3: 초당 200건 이상 → 6,000건을 30초 안에
  private static final int MESSAGES = 120;
  private static final int PER_MESSAGE = 50;
  private static final Duration LIMIT = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-029 NFR-3] stage.timing 기록 6,000건을 Pod 하나에서 30초 안에 적재한다")
  void ingestsSixThousandWithinThirtySeconds() {
    Instant at = Instant.now().minusSeconds(60);
    CompletableFuture<?>[] sends = new CompletableFuture<?>[MESSAGES];
    long started = System.nanoTime();
    for (int m = 0; m < MESSAGES; m++) {
      UUID source = UUID.randomUUID();
      StageTimings batch = StageTimings.batch();
      for (int i = 0; i < PER_MESSAGE; i++) {
        batch.timing(source, "analyzer.judge", at.plusMillis(i), 10 + i);
      }
      sends[m] = kafka.send("stage.timing", source.toString(), batch.json());
    }
    CompletableFuture.allOf(sends).join();

    await().atMost(LIMIT).until(() -> count() == MESSAGES * PER_MESSAGE);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

    assertThat(elapsed).isLessThanOrEqualTo(LIMIT);
  }

  private int count() {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM stage_timings", Integer.class);
    return count == null ? 0 : count;
  }
}
