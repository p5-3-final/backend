package com.vlm.quality.review.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.review.domain.ReviewLabel;
import com.vlm.quality.review.domain.ReviewLabelRules;
import com.vlm.quality.review.domain.ReviewStatus;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검토 라벨 저장(SPEC-029 R-8, R-9). */
@Service
@RequiredArgsConstructor
public class ReviewLabelService {

  private final ReviewItemRepository items;
  private final ReviewQueryService queries;
  private final Clock clock;

  /** 맡은 사람만 저장한다. LABELED 항목을 같은 사람이 다시 저장하면 덮어쓴다. 형식 검사는 갱신 전에 한다. */
  @Transactional
  public ReviewItemView label(UUID itemId, UUID operator, ReviewLabel label) {
    ReviewLabelRules.check(label);
    if (items.labelIfAssignee(itemId, operator, label, clock.instant()) == 0) {
      ReviewItem current = queries.find(itemId);
      throw new BusinessException(
          current.status() == ReviewStatus.UNASSIGNED
              ? QualityErrorCode.REVIEW_NOT_ASSIGNED
              : QualityErrorCode.REVIEW_NOT_ASSIGNEE);
    }
    return queries.view(queries.find(itemId));
  }
}
