package com.vlm.quality.evaluation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * CI Gate 결과(R-18). 받을 때 계산해 저장하고 기준 실행이 바뀌어도 다시 계산하지 않는다.
 *
 * @param baselineRunId 받을 때의 기준 실행. 없으면 null
 * @param passed 기준이 없으면 null
 * @param failures 나빠진 지표와 기준에만 있는 지표(value null)
 */
public record GateResult(
    @Nullable UUID baselineRunId,
    @Nullable Boolean passed,
    BigDecimal tolerance,
    List<GateFailure> failures) {

  public GateResult {
    failures = List.copyOf(failures);
  }
}
