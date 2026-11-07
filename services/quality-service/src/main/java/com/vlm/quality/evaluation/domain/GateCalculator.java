package com.vlm.quality.evaluation.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 평가 실행을 기준 실행과 비교한다(SPEC-029 R-18).
 *
 * <p>같은 이름·유형의 지표끼리 짝짓는다. 좋아지는 방향은 기준 실행의 {@code higherIsBetter}를 따른다. 값은 {@link BigDecimal}로 비교해
 * 허용폭 경계(예: 0.78 − 0.02 = 0.76)에서 부동소수 오차로 결과가 바뀌지 않게 한다.
 */
public final class GateCalculator {

  private GateCalculator() {}

  /**
   * 비교 결과를 만든다.
   *
   * @param baselineRunId 기준 실행. 없으면 null이고 결과는 {@code passed: null}이다
   * @param baseline 기준 실행의 지표
   * @param tolerance 허용 하락폭(절댓값)
   */
  public static GateResult compare(
      List<Metric> candidate,
      @Nullable UUID baselineRunId,
      List<Metric> baseline,
      BigDecimal tolerance) {
    if (baselineRunId == null) {
      return new GateResult(null, null, tolerance, List.of());
    }
    Map<MetricKey, Metric> byKey = new LinkedHashMap<>();
    candidate.forEach(metric -> byKey.put(MetricKey.of(metric), metric));
    List<GateFailure> failures = new ArrayList<>();
    for (Metric reference : baseline) {
      Metric current = byKey.get(MetricKey.of(reference));
      if (current == null) {
        // 이번 실행에서 빠진 지표는 비교할 값이 없어 실패로 본다(계약 failures[].value null).
        failures.add(
            new GateFailure(reference.name(), reference.eventType(), reference.value(), null));
      } else if (worse(current.value(), reference, tolerance)) {
        failures.add(
            new GateFailure(
                reference.name(), reference.eventType(), reference.value(), current.value()));
      }
    }
    return new GateResult(baselineRunId, failures.isEmpty(), tolerance, failures);
  }

  private static boolean worse(BigDecimal value, Metric reference, BigDecimal tolerance) {
    return reference.higherIsBetter()
        ? value.compareTo(reference.value().subtract(tolerance)) < 0
        : value.compareTo(reference.value().add(tolerance)) > 0;
  }
}
