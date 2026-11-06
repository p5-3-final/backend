package com.vlm.media.retention.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RetainedVideoRepository {

  RetainedVideo save(RetainedVideo retainedVideo);

  /** 행을 잠그고 조회한다. 같은 기록의 철회를 한 번에 하나씩 처리하려고 쓴다(SPEC-018 NFR-3). */
  Optional<RetainedVideo> findBySourceIdForUpdate(UUID sourceId);

  void delete(RetainedVideo retainedVideo);

  /** 소유자의 기록을 삭제 시각 최신순으로 조회한다. */
  RetainedVideoPage findByOwner(UUID ownerId, int page, int size);

  /** 보존 만료 시각이 지난 기록을 다른 트랜잭션이 잠근 행은 건너뛰고 최대 {@code limit}개 지운다. */
  int deleteExpired(Instant now, int limit);
}
