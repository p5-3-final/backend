package com.vlm.quality.stage.domain;

/** 단계 경로 하나의 집계(계약 StageStat). 백분위는 음수 기록을 뺀 값이다(R-22). */
public record StageStat(
    String path,
    String stageId,
    long count,
    double totalMs,
    double p50Ms,
    double p95Ms,
    long errorCount) {}
