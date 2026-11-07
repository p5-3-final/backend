package com.vlm.quality.stage.domain;

import java.util.List;

/** 성능 Gate 결과(SPEC-030 R-16~R-18). 저장하지 않는다. */
public record StageGate(boolean passed, double maxRegressionRatio, List<StageVerdict> stages) {

  public StageGate {
    stages = List.copyOf(stages);
  }
}
