package com.vlm.pipeline.domain;

import java.util.List;

/** 저장소에서 복원한 계획. 실행 상태를 재시도로 변경하지 않는다. */
public record JobPlan(PlanInput input, String status, List<ChunkPlan> chunks) {
  public JobPlan {
    chunks = List.copyOf(chunks);
  }
}
