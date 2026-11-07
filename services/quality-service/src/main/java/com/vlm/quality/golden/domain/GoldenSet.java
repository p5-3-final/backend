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
 * @param draftEntryCount 초안 항목 수. 데이터셋 세트(import)는 데이터셋 항목 수다
 * @param datasetOriginJson 데이터셋 세트의 출처·사용 조건(R-10 datasetOrigin) JSON. 검토 세트는 null
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
    Instant createdAt,
    @Nullable String datasetOriginJson) {

  public static final String ORIGIN_REVIEW = "review";
  public static final String ORIGIN_IMPORT = "import";

  public boolean isDataset() {
    return ORIGIN_IMPORT.equals(origin);
  }
}
