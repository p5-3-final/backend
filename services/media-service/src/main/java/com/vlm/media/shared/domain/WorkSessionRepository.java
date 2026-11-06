package com.vlm.media.shared.domain;

import java.util.Optional;
import java.util.UUID;

public interface WorkSessionRepository {

  WorkSession save(WorkSession session);

  Optional<WorkSession> findByPublicId(UUID publicId);

  /** 행을 잠그고 조회한다. 세션에 영상을 넣는 요청을 한 번에 하나씩 세려고 쓴다(SPEC-017 NFR-2). */
  Optional<WorkSession> findByPublicIdForUpdate(UUID publicId);

  /** 소유자의 세션을 만든 시각 최신순으로 조회한다. */
  WorkSessionPage findByOwner(UUID ownerId, int page, int size);
}
