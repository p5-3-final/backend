package com.vlm.quality.stage.application;

import com.vlm.quality.stage.domain.StageTiming;
import java.util.List;

/** stage.timing 메시지 하나의 기록들. */
public record StageTimingBatch(List<StageTiming> timings) {

  public StageTimingBatch {
    timings = List.copyOf(timings);
  }
}
