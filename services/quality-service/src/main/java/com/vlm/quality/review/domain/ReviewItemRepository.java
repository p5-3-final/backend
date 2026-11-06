package com.vlm.quality.review.domain;

import com.vlm.quality.shared.domain.ReviewLabel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 검토 항목 저장소. 상태 전이는 모두 조건부 갱신이라 동시 요청 중 하나만 1행을 바꾼다(NFR-1). */
public interface ReviewItemRepository {

  /** 같은 eventId 항목이 없을 때만 넣는다(R-3). 넣었으면 true. */
  boolean insertIfAbsent(NewReviewItem item);

  Optional<ReviewItem> findByItemId(UUID itemId);

  ReviewItemPage list(ReviewFilter filter, int page, int size);

  /** UNASSIGNED면 요청자에게 맡긴다. 바꾼 행 수. */
  int assignIfUnassigned(UUID itemId, UUID assignee, Instant now);

  /** 요청자가 맡은 ASSIGNED 항목을 UNASSIGNED로 되돌린다. 바꾼 행 수. */
  int releaseIfAssignee(UUID itemId, UUID assignee, Instant now);

  /** 요청자가 맡은(ASSIGNED·LABELED) 항목에 라벨을 저장한다. 바꾼 행 수. */
  int labelIfAssignee(UUID itemId, UUID assignee, ReviewLabel label, Instant now);

  /** cutoff 전에 맡고 라벨이 없는 항목을 되돌린다(R-10). 되돌린 수. */
  int releaseAssignedBefore(Instant cutoff, Instant now);

  /** 그 영상의 검토 항목을 지운다(R-16). 지운 수. 초안 항목은 FK cascade로 함께 지워진다. */
  int deleteBySource(UUID sourceId);
}
