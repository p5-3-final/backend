package com.vlm.quality.export.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 내보내기 작업 행. 가져오기와 같은 방식으로 DB 행을 작업 큐로 쓴다. */
public interface FeatureExportRepository {

  void insert(UUID exportId, ExportScope scope, UUID requestedBy, Instant now);

  Optional<FeatureExport> findByExportId(UUID exportId);

  /** PENDING이나 작업 표시가 staleBefore보다 오래된 RUNNING 하나를 RUNNING으로 가져간다. */
  Optional<FeatureExport> claim(Instant now, Instant staleBefore);

  void heartbeat(long id, Instant now);

  void complete(long id, String fileKey, int rowCount, int sourceCount, Instant now);

  void fail(long id, Instant now);
}
