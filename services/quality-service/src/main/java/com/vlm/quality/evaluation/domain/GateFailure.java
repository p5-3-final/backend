package com.vlm.quality.evaluation.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/** 기준보다 허용폭 넘게 나빠진 지표(계약 GateResult.failures). */
public record GateFailure(
    String name, @Nullable String eventType, BigDecimal baseline, BigDecimal value) {}
