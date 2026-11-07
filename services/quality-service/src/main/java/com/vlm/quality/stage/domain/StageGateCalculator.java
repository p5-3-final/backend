package com.vlm.quality.stage.domain;

import com.vlm.quality.stage.domain.StageVerdict.Status;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 단계별 p95 회귀 판정(SPEC-030 R-16~R-18). */
public final class StageGateCalculator {

  private StageGateCalculator() {}

  /**
   * 단계마다 후보 p95 / 기준 p95 − 1을 maxRegressionRatio와 비교한다.
   *
   * @param minSamples 한쪽 기록 수가 이보다 적으면 그 단계는 skipped다(R-17)
   */
  public static StageGate evaluate(
      List<StageSample> samples, double maxRegressionRatio, int minSamples) {
    List<StageVerdict> verdicts =
        samples.stream().map(sample -> verdict(sample, maxRegressionRatio, minSamples)).toList();
    boolean passed = verdicts.stream().noneMatch(v -> v.status() == Status.FAILED);
    return new StageGate(passed, maxRegressionRatio, verdicts);
  }

  private static StageVerdict verdict(
      StageSample sample, double maxRegressionRatio, int minSamples) {
    Double ratio = ratio(sample.baselineP95Ms(), sample.candidateP95Ms());
    if (ratio == null
        || sample.baselineCount() < minSamples
        || sample.candidateCount() < minSamples) {
      return new StageVerdict(sample, ratio, Status.SKIPPED);
    }
    Status status = ratio - 1 > maxRegressionRatio ? Status.FAILED : Status.PASSED;
    return new StageVerdict(sample, ratio, status);
  }

  // 기준 p95가 0이면 어떤 후보 값도 무한대 회귀가 되어 판정할 수 없다.
  private static @Nullable Double ratio(@Nullable Double baseline, @Nullable Double candidate) {
    if (baseline == null || candidate == null || baseline <= 0) {
      return null;
    }
    return candidate / baseline;
  }
}
