package com.vlm.quality.stage.domain;

import java.time.LocalDate;

/** 일 단위 집계 하루(계약 StageTrend.days). */
public record TrendDay(LocalDate date, long count, double p50Ms, double p95Ms, long errorCount) {}
