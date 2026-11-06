package com.vlm.quality.review.domain;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;

/** 라벨 형식 규칙(SPEC-029 R-9). 저장 전에 검사해, 어기면 항목을 바꾸지 않는다. */
public final class ReviewLabelRules {

  private ReviewLabelRules() {}

  /**
   * 라벨이 규칙에 맞는지 본다.
   *
   * @throws BusinessException 규칙을 어기면 {@link QualityErrorCode#INVALID_REQUEST}
   */
  public static void check(ReviewLabel label) {
    if (label.eventPresent()) {
      Long start = label.startMs();
      Long end = label.endMs();
      if (label.eventType() == null || start == null || end == null) {
        throw invalid("eventPresent=true면 eventType·startMs·endMs가 모두 있어야 합니다.");
      }
      if (end <= start) {
        throw invalid("endMs는 startMs보다 커야 합니다.");
      }
    } else if (label.eventType() != null) {
      throw invalid("eventPresent=false면 eventType이 없어야 합니다.");
    }
  }

  private static BusinessException invalid(String detail) {
    return new BusinessException(QualityErrorCode.INVALID_REQUEST, detail);
  }
}
