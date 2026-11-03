package com.vlm.pipeline.domain;

import java.util.List;

/** 외부 의존성 없이 청크 구간을 계산한다. */
public final class ChunkPlanner {
  private ChunkPlanner() {}

  public static List<ChunkPlan> calculate(PlanInput input) {
    var chunks = new java.util.ArrayList<ChunkPlan>();
    for (var start = java.math.BigDecimal.ZERO;
        start.compareTo(input.duration()) < 0;
        start = start.add(input.chunkLength())) {
      var end = start.add(input.chunkLength()).min(input.duration());
      chunks.add(
          new ChunkPlan(
              chunks.size(),
              start,
              end,
              start.subtract(input.overlap()).max(java.math.BigDecimal.ZERO),
              end.add(input.overlap()).min(input.duration())));
    }
    return List.copyOf(chunks);
  }
}
