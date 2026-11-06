package com.vlm.media.shared.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VideoRepository {

  Video save(Video video);

  Optional<Video> findByPublicId(UUID publicId);

  /** 소유자의 삭제되지 않은 영상을 만든 시각 최신순으로 조회한다. */
  VideoPage findVisibleByOwner(UUID ownerId, int page, int size);

  /** 세션에 속한 "삭제"가 아닌 영상. 순서는 정하지 않는다({@link SessionOrder}로 정렬한다). */
  List<Video> findVisibleInSession(UUID sessionId);

  /** 세션에 속한 "삭제"가 아닌 영상 수. */
  long countVisibleInSession(UUID sessionId);

  /** 행을 잠그고 조회한다. 같은 영상의 상태 전이를 한 요청씩 처리하려고 쓴다(SPEC-003 NFR-2). */
  Optional<Video> findByPublicIdForUpdate(UUID publicId);

  /** 저장소 정리가 끝나지 않은 만료·삭제 영상을 다른 트랜잭션이 잠근 행은 건너뛰고 잠가 가져온다. */
  List<Video> lockStorageCleanupTargets(int limit);

  /** 만료 기한이 지난 "업로드 중" 영상을 다른 트랜잭션이 잠근 행은 건너뛰고 잠가 가져온다. */
  List<Video> lockExpiredUploads(Instant now, int limit);
}
