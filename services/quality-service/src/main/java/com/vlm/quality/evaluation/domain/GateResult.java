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
 * @param missingInCandidate 기준에만 있는 지표. 계약 failures[].value가 null을 허용하지 않아 failures에 넣지 못하고 passed만
 *     false로 만든다(계약 C-30 대기). 저장하지 않고 받을 때 로그로 남긴다
 */
public record GateResult(
    @Nullable UUID baselineRunId,
    @Nullable Boolean passed,
    BigDecimal tolerance,
    List<GateFailure> failures,
    List<MetricKey> missingInCandidate) {

  public GateResult {
    failures = List.copyOf(failures);
    missingInCandidate = List.copyOf(missingInCandidate);
  }
}
