package com.vlm.quality.review.domain;

import org.jspecify.annotations.Nullable;

/** 검토 큐 목록 필터(R-5, R-27). null이면 거르지 않는다. */
public record ReviewFilter(
    @Nullable ReviewStatus status, @Nullable ReviewReason reason, @Nullable String eventType) {}
