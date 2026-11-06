package com.vlm.quality.review.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import com.vlm.quality.shared.domain.ReviewLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewLabelRulesTest {

  @Test
  @DisplayName("[SPEC-029 R-9] eventPresent=true면 유형·시작·끝이 모두 있고 끝이 시작보다 커야 한다")
  void requiresIntervalWhenPresent() {
    assertInvalid(new ReviewLabel("correct", true, null, 0L, 10L, null, null));
    assertInvalid(new ReviewLabel("correct", true, "fall", null, 10L, null, null));
    assertInvalid(new ReviewLabel("correct", true, "fall", 0L, null, null, null));
    assertInvalid(new ReviewLabel("correct", true, "fall", 10L, 10L, null, null));
    assertThatCode(
            () ->
                ReviewLabelRules.check(
                    new ReviewLabel("correct", true, "fall", 0L, 1L, null, null)))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("[SPEC-029 R-9] eventPresent=false면 유형이 없어야 한다")
  void forbidsTypeWhenAbsent() {
    assertInvalid(new ReviewLabel("incorrect", false, "fall", null, null, null, null));
    assertThatCode(
            () ->
                ReviewLabelRules.check(
                    new ReviewLabel("incorrect", false, null, null, null, null, "없음")))
        .doesNotThrowAnyException();
  }

  private static void assertInvalid(ReviewLabel label) {
    assertThatThrownBy(() -> ReviewLabelRules.check(label))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(QualityErrorCode.INVALID_REQUEST);
  }
}
