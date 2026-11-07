package com.vlm.quality.evaluation.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * 기준보다 허용폭 넘게 나빠졌거나 이번 실행에서 빠진 지표(계약 GateResult.failures).
 *
 * @param value 이번 실행의 값. 기준 실행에만 있는 지표면 null이다(R-18)
 */
public record GateFailure(
    String name, @Nullable String eventType, BigDecimal baseline, @Nullable BigDecimal value) {}
