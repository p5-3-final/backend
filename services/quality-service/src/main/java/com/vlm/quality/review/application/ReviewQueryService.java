package com.vlm.quality.review.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.review.domain.ReviewFilter;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewItemPage;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.shared.domain.EvidenceUrls;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 검토 큐 목록·상세(SPEC-029 R-5, R-27). */
@Service
@RequiredArgsConstructor
public class ReviewQueryService {

  private final ReviewItemRepository items;
  private final EvidenceUrls evidenceUrls;

  @Transactional(readOnly = true)
  public ReviewItemViewPage list(ReviewFilter filter, int page, int size) {
    ReviewItemPage found = items.list(filter, page, size);
    return new ReviewItemViewPage(
        found.items().stream().map(this::view).toList(), found.totalElements());
  }

  @Transactional(readOnly = true)
  public ReviewItemView get(UUID itemId) {
    return view(find(itemId));
  }

  /**
   * 항목을 찾는다. Golden Set 초안에 넣을 때도 쓴다(R-12).
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#REVIEW_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public ReviewItem find(UUID itemId) {
    return items
        .findByItemId(itemId)
        .orElseThrow(() -> new BusinessException(QualityErrorCode.REVIEW_NOT_FOUND));
  }

  ReviewItemView view(ReviewItem item) {
    return new ReviewItemView(item, evidenceUrls.sign(item.evidenceFrameKeys()));
  }
}
