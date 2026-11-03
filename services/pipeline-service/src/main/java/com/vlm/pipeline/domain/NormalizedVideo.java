package com.vlm.pipeline.domain;

import java.util.UUID;

/** 검증된 정규화 결과. payload·metadata는 JSONB 중복/불변성 검증에 사용한다. */
public record NormalizedVideo(
    UUID eventId,
    UUID normalizationId,
    UUID sourceId,
    UUID ownerId,
    String version,
    boolean ready,
    long durationMs,
    String payload,
    String metadata) {}
