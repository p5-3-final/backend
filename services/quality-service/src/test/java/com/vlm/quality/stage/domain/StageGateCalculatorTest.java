package com.vlm.quality.stage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vlm.quality.stage.domain.StageVerdict.Status;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StageGateCalculatorTest {

  private static final double RATIO = 0.2;
  private static final int MIN = 5;

  @Test
  @DisplayName("[SPEC-030 R-16] 기준 3,400ms → 후보 4,300ms면 비율 1.265라 0.2를 넘어 failed다")
  void failsOnRegression() {
    StageGate gate =
        StageGateCalculator.evaluate(
            List.of(new StageSample("worker.vlm", 10, 3400.0, 10, 4300.0)), RATIO, MIN);

    StageVerdict verdict = gate.stages().get(0);
    assertThat(verdict.status()).isEqualTo(Status.FAILED);
    assertThat(verdict.ratio()).isCloseTo(4300.0 / 3400.0, within(1e-9));
    assertThat(gate.passed()).isFalse();
    assertThat(gate.maxRegressionRatio()).isEqualTo(RATIO);
  }

  @Test
  @DisplayName("[SPEC-030 R-16] 비율 − 1이 maxRegressionRatio와 같거나 작으면 passed다")
  void passesWithinThreshold() {
    StageGate gate =
        StageGateCalculator.evaluate(
            List.of(
                new StageSample("a", 5, 1000.0, 5, 1200.0),
                new StageSample("b", 5, 1000.0, 5, 800.0)),
            RATIO,
            MIN);

    assertThat(gate.stages()).extracting(StageVerdict::status).containsOnly(Status.PASSED);
    assertThat(gate.passed()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-030 R-17] 한쪽 기록 수가 minSamples보다 적으면 skipped이고 결과에 영향이 없다")
  void skipsSmallSamples() {
    StageGate gate =
        StageGateCalculator.evaluate(
            List.of(
                new StageSample("few-baseline", 4, 1000.0, 10, 9000.0),
                new StageSample("few-candidate", 10, 1000.0, 4, 9000.0),
                new StageSample("missing", 10, 1000.0, 0, null),
                new StageSample("ok", 10, 1000.0, 10, 1000.0)),
            RATIO,
            MIN);

    assertThat(gate.stages())
        .extracting(StageVerdict::status)
        .containsExactly(Status.SKIPPED, Status.SKIPPED, Status.SKIPPED, Status.PASSED);
    assertThat(gate.stages().get(0).ratio()).isCloseTo(9.0, within(1e-9));
    assertThat(gate.stages().get(2).ratio()).isNull();
    assertThat(gate.passed()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-030 R-17] 기준 p95가 0이면 비율을 정할 수 없어 skipped다")
  void skipsZeroBaseline() {
    StageVerdict verdict =
        StageGateCalculator.evaluate(
                List.of(new StageSample("zero", 10, 0.0, 10, 50.0)), RATIO, MIN)
            .stages()
            .get(0);

    assertThat(verdict.status()).isEqualTo(Status.SKIPPED);
    assertThat(verdict.ratio()).isNull();
  }

  @Test
  @DisplayName("[SPEC-030 R-18] 단계가 모두 skipped이거나 없으면 passed: true다")
  void passesWithoutVerdicts() {
    assertThat(StageGateCalculator.evaluate(List.of(), RATIO, MIN).passed()).isTrue();
    assertThat(
            StageGateCalculator.evaluate(
                    List.of(new StageSample("x", 1, 1.0, 1, 100.0)), RATIO, MIN)
                .passed())
        .isTrue();
  }
}
