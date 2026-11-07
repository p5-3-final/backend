package com.vlm.quality.stage.domain;

import org.jspecify.annotations.Nullable;

/**
 * 한 단계의 기준·후보 기록 수와 p95(SPEC-030 R-16). 기록이 없는 쪽의 p95는 null이다.
 *
 * <p>음수 durationMs는 뺀 값이다(SPEC-029 R-22).
 */
public record StageSample(
    String stageId,
    int baselineCount,
    @Nullable Double baselineP95Ms,
    int candidateCount,
    @Nullable Double candidateP95Ms) {}
