package com.vlm.media.domain;

import java.util.Optional;
import java.util.UUID;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findByPublicId(UUID publicId);

  /** 행을 잠그고 조회한다. 같은 영상의 상태 전이를 한 요청씩 처리하려고 쓴다(SPEC-003 NFR-2). */
  Optional<Video> findByPublicIdForUpdate(UUID publicId);
}
