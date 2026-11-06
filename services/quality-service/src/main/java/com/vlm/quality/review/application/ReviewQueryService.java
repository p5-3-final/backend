package com.vlm.quality.review.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.review.domain.ReviewFilter;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewItemPage;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.review.domain.ReviewReason;
import com.vlm.quality.review.domain.ReviewStatus;
import com.vlm.quality.shared.domain.EvidenceUrls;
import com.vlm.quality.shared.domain.QualityErrorCode;
import com.vlm.quality.shared.domain.ReviewLabel;
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

  /**
   * Golden Set 초안에 넣을 항목을 고른다. LABELED이고 verdict가 uncertain이 아니며 판정 결과가 있는 항목만이다(R-12).
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#REVIEW_NOT_FOUND}, 넣을 수 없으면 {@link
   *     QualityErrorCode#REVIEW_NOT_LABELED}
   */
  @Transactional(readOnly = true)
  public GoldenCandidate goldenCandidate(UUID itemId) {
    ReviewItem item = find(itemId);
    ReviewLabel label = item.label();
    // alert_dismissed 항목은 판정 결과(VLMAnalysis)가 없어 매니페스트 entries.analysis를 채울 수 없다.
    if (item.status() != ReviewStatus.LABELED
        || label == null
        || !label.hasDecidedVerdict()
        || item.reason() == ReviewReason.ALERT_DISMISSED) {
      throw new BusinessException(QualityErrorCode.REVIEW_NOT_LABELED);
    }
    return new GoldenCandidate(item.id(), item.itemId(), item.sourceId(), item.eventId(), label);
  }

  ReviewItemView view(ReviewItem item) {
    return new ReviewItemView(item, evidenceUrls.sign(item.evidenceFrameKeys()));
  }
}
