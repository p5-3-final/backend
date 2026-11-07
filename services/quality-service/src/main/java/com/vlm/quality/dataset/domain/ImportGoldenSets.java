package com.vlm.quality.dataset.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 가져오기가 쓴 Golden Set과 확정 버전(계약 {@code DatasetImport.goldenSets}). 확정까지 가지 못했으면 {@link #NONE}이다. */
public record ImportGoldenSets(
    @Nullable UUID devSetId,
    @Nullable UUID evalSetId,
    @Nullable Integer devVersion,
    @Nullable Integer evalVersion) {

  public static final ImportGoldenSets NONE = new ImportGoldenSets(null, null, null, null);
}
