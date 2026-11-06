package com.vlm.quality.review.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.review.domain.ReviewStatus;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검토 항목 맡기·내려놓기(SPEC-029 R-6, R-7, NFR-1).
 *
 * <p>상태를 읽고 쓰지 않고 조건부 UPDATE 한 문장으로 바꾼다. 행 잠금으로 동시 요청 중 하나만 1행을 바꾸고, 0행이면 다시 읽어 실패 이유를 고른다.
 */
@Service
@RequiredArgsConstructor
public class ReviewAssignmentService {

  private final ReviewItemRepository items;
  private final ReviewQueryService queries;
  private final Clock clock;

  @Transactional
  public ReviewItemView assign(UUID itemId, UUID operator) {
    if (items.assignIfUnassigned(itemId, operator, clock.instant()) == 0) {
      ReviewItem current = queries.find(itemId);
      throw new BusinessException(
          current.status() == ReviewStatus.LABELED
              ? QualityErrorCode.REVIEW_ALREADY_LABELED
              : QualityErrorCode.REVIEW_ALREADY_ASSIGNED);
    }
    return queries.view(queries.find(itemId));
  }

  @Transactional
  public void release(UUID itemId, UUID operator) {
    if (items.releaseIfAssignee(itemId, operator, clock.instant()) == 0) {
      ReviewItem current = queries.find(itemId);
      throw new BusinessException(
          current.status() == ReviewStatus.ASSIGNED
              ? QualityErrorCode.REVIEW_NOT_ASSIGNEE
              : QualityErrorCode.REVIEW_NOT_ASSIGNED);
    }
  }
}
