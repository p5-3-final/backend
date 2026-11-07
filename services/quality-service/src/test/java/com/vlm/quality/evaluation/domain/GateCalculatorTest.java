package com.vlm.quality.evaluation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GateCalculatorTest {

  private static final UUID BASELINE = UUID.randomUUID();
  private static final BigDecimal TOLERANCE = new BigDecimal("0.02");

  @Test
  @DisplayName("[SPEC-029 R-18] 기준 0.78·허용 0.02에서 0.74면 passed=false이고 실패에 judge_precision이 있다")
  void failsWhenHigherIsBetterDrops() {
    GateResult result =
        GateCalculator.compare(
            List.of(
                metric("judge_precision", "0.74", true), metric("candidate_recall", "0.81", true)),
            BASELINE,
            List.of(
                metric("judge_precision", "0.78", true), metric("candidate_recall", "0.80", true)),
            TOLERANCE);

    assertThat(result.passed()).isFalse();
    assertThat(result.baselineRunId()).isEqualTo(BASELINE);
    assertThat(result.failures())
        .containsExactly(
            new GateFailure(
                "judge_precision", "ppe_missing", new BigDecimal("0.78"), new BigDecimal("0.74")));
  }

  @Test
  @DisplayName("[SPEC-029 R-18] 낮을수록 좋은 지표는 기준 + 허용폭보다 크면 실패다")
  void failsWhenLowerIsBetterRises() {
    GateResult result =
        GateCalculator.compare(
            List.of(metric("false_alarm_rate", "0.13", false)),
            BASELINE,
            List.of(metric("false_alarm_rate", "0.10", false)),
            TOLERANCE);

    assertThat(result.passed()).isFalse();
    assertThat(result.failures()).extracting(GateFailure::name).containsExactly("false_alarm_rate");
  }

  @Test
  @DisplayName("[SPEC-029 R-18] 허용폭 경계(정확히 기준 - 0.02) 안이면 passed=true이고 실패가 없다")
  void passesWithinTolerance() {
    GateResult result =
        GateCalculator.compare(
            List.of(
                metric("judge_precision", "0.76", true), metric("false_alarm_rate", "0.12", false)),
            BASELINE,
            List.of(
                metric("judge_precision", "0.78", true), metric("false_alarm_rate", "0.10", false)),
            TOLERANCE);

    assertThat(result.passed()).isTrue();
    assertThat(result.failures()).isEmpty();
  }

  @Test
  @DisplayName("[SPEC-029 R-18] 유형이 다른 지표는 따로 비교하고, 후보에만 있는 지표는 실패가 아니다")
  void matchesByNameAndEventType() {
    GateResult result =
        GateCalculator.compare(
            List.of(
                new Metric("judge_precision", "fall", new BigDecimal("0.50"), true),
                metric("judge_precision", "0.78", true),
                metric("new_metric", "0.10", true)),
            BASELINE,
            List.of(
                new Metric("judge_precision", "fall", new BigDecimal("0.50"), true),
                metric("judge_precision", "0.78", true)),
            TOLERANCE);

    assertThat(result.passed()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-029 R-18] 기준 실행이 없으면 passed=null이고 실패가 없다")
  void nullWithoutBaseline() {
    GateResult result =
        GateCalculator.compare(
            List.of(metric("judge_precision", "0.10", true)), null, List.of(), TOLERANCE);

    assertThat(result.passed()).isNull();
    assertThat(result.baselineRunId()).isNull();
    assertThat(result.failures()).isEmpty();
  }

  @Test
  @DisplayName("[SPEC-029 R-18] 기준에만 있는 지표가 있으면 passed=false이고 실패 목록에 value null로 넣는다")
  void baselineOnlyMetricFailsGate() {
    GateResult result =
        GateCalculator.compare(
            List.of(metric("judge_precision", "0.78", true)),
            BASELINE,
            List.of(
                metric("judge_precision", "0.78", true), metric("candidate_recall", "0.80", true)),
            TOLERANCE);

    assertThat(result.passed()).isFalse();
    assertThat(result.failures())
        .containsExactly(
            new GateFailure("candidate_recall", "ppe_missing", new BigDecimal("0.80"), null));
  }

  private static Metric metric(String name, String value, boolean higherIsBetter) {
    return new Metric(name, "ppe_missing", new BigDecimal(value), higherIsBetter);
  }
}
