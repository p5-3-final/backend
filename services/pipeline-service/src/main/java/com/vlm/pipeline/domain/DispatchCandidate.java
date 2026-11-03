package com.vlm.pipeline.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 잠긴 청크와 SPEC-008에 저장된 불변 정규화·설정 참조. */
public record DispatchCandidate(
    long id,
    UUID chunkId,
    UUID jobId,
    UUID sourceId,
    UUID ownerId,
    UUID normalizationId,
    String normalizedKey,
    long ownedStart,
    long ownedEnd,
    long processingStart,
    long processingEnd,
    AnalysisSettings settings,
    @Nullable String traceparent) {}
