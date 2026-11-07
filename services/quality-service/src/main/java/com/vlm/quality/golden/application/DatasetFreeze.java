package com.vlm.quality.golden.application;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 데이터셋 Golden Set 쓰기 결과(계약 {@code DatasetImport.goldenSets}).
 *
 * @param devVersion 그 분할에 이번 항목이 없어 확정하지 않았으면 null(R-8)
 */
public record DatasetFreeze(
    UUID devSetId, UUID evalSetId, @Nullable Integer devVersion, @Nullable Integer evalVersion) {}
