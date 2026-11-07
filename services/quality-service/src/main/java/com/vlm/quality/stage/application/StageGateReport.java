package com.vlm.quality.stage.application;

import com.vlm.quality.stage.domain.StageGate;
import com.vlm.quality.stage.domain.StageSample;
import com.vlm.quality.stage.domain.StageVerdict;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 다른 기능(evaluation 내부 API)에 돌려주는 성능 Gate 결과(계약 StageGateResult). */
public record StageGateReport(boolean passed, double maxRegressionRatio, List<Stage> stages) {

  public StageGateReport {
    stages = List.copyOf(stages);
  }

  /**
   * 한 단계의 판정.
   *
   * @param status passed·failed·skipped
   */
  public record Stage(
      String stageId,
      @Nullable Double baselineP95Ms,
      @Nullable Double candidateP95Ms,
      @Nullable Double ratio,
      int baselineCount,
      int candidateCount,
      String status) {}

  static StageGateReport of(StageGate gate) {
    return new StageGateReport(
        gate.passed(),
        gate.maxRegressionRatio(),
        gate.stages().stream().map(StageGateReport::stage).toList());
  }

  private static Stage stage(StageVerdict verdict) {
    StageSample sample = verdict.sample();
    return new Stage(
        sample.stageId(),
        sample.baselineP95Ms(),
        sample.candidateP95Ms(),
        verdict.ratio(),
        sample.baselineCount(),
        sample.candidateCount(),
        verdict.status().wire());
  }
}
