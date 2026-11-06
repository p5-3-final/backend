package com.vlm.quality.golden.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Golden Set(SPEC-029 R-11).
 *
 * @param id 내부 행 ID
 * @param split dev·eval
 * @param origin review(검토 라벨)·import(SPEC-030)
 * @param draftEntryCount 초안 항목 수
 */
public record GoldenSet(
    long id,
    UUID setId,
    String name,
    String split,
    String origin,
    @Nullable String description,
    @Nullable Integer latestVersion,
    @Nullable UUID baselineRunId,
    int draftEntryCount,
    Instant createdAt) {

  public static final String ORIGIN_REVIEW = "review";
}
