package com.vlm.quality.export.domain;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 특징 내보내기 하나(SPEC-030 R-14). */
public record FeatureExport(
    long id,
    UUID exportId,
    ExportStatus status,
    ExportScope scope,
    @Nullable String fileKey,
    int rowCount,
    int sourceCount,
    UUID requestedBy,
    Instant requestedAt,
    @Nullable Instant finishedAt) {

  /** 계약 FeatureExport.fileKey 형식. */
  public static String fileKeyOf(UUID exportId) {
    return "exports/" + exportId + "/features.jsonl";
  }
}
