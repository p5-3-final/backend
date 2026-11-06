package com.vlm.quality.evaluation.domain;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * 평가 지표 하나(계약 Metric).
 *
 * @param eventType 유형별 지표면 유형, 전체면 null
 */
public record Metric(
    String name, @Nullable String eventType, BigDecimal value, boolean higherIsBetter) {}
