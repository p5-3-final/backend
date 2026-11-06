package com.vlm.quality.support;

import com.vlm.quality.stage.application.StageTimingBatch;
import com.vlm.quality.stage.domain.StageTiming;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Kafka 없이 측정 기록을 바로 적재할 때 쓰는 값. */
public final class StageFixtures {

  private StageFixtures() {}

  public static StageTiming timing(
      @Nullable UUID sourceId,
      String stageId,
      Instant startedAt,
      double durationMs,
      String status) {
    return timing(sourceId, stageId, startedAt, durationMs, status, Map.of());
  }

  public static StageTiming timing(
      @Nullable UUID sourceId,
      String stageId,
      Instant startedAt,
      double durationMs,
      String status,
      Map<String, String> versions) {
    return new StageTiming(
        UUID.randomUUID(),
        stageId,
        "event-analyzer.handle/" + stageId,
        sourceId,
        null,
        "event-analyzer",
        1,
        startedAt,
        startedAt.plusMillis((long) Math.max(durationMs, 0)),
        durationMs,
        status,
        versions);
  }

  public static StageTimingBatch batch(StageTiming... timings) {
    return new StageTimingBatch(List.of(timings));
  }
}
