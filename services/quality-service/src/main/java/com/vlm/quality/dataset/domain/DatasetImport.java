package com.vlm.quality.dataset.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 외부 데이터셋 가져오기(SPEC-030 R-1, R-9).
 *
 * @param id 내부 행 ID
 * @param datasetName 매니페스트를 읽기 전에는 null
 * @param devSetId 만든(또는 찾은) Golden Set. 확정까지 가지 않았으면 null
 * @param devVersion 그 분할에 이번 가져오기 항목이 없으면 확정하지 않아 null(R-8)
 * @param datasetOriginJson 매니페스트 origin(JSON). 검증 전에는 null
 */
public record DatasetImport(
    long id,
    UUID importId,
    @Nullable String datasetName,
    String manifestKey,
    double evalRatio,
    long splitSeed,
    ImportStatus status,
    ImportCounts counts,
    @Nullable UUID devSetId,
    @Nullable UUID evalSetId,
    @Nullable Integer devVersion,
    @Nullable Integer evalVersion,
    List<ImportError> errors,
    UUID requestedBy,
    Instant requestedAt,
    @Nullable Instant finishedAt,
    @Nullable String datasetOriginJson) {

  public DatasetImport {
    errors = List.copyOf(errors);
  }

  /** 매니페스트 경로의 폴더({@code imports/<폴더>/}). 영상 경로의 기준이다. */
  public String folder() {
    return manifestKey.substring(0, manifestKey.lastIndexOf('/') + 1);
  }
}
