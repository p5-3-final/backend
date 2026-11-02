package com.vlm.media.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UploadPlanTest {

  // 계약 기본값: 100MB 이하 단일, 조각 64MiB
  private static final long SINGLE_MAX = 104_857_600;
  private static final long PART = 67_108_864;

  @Test
  @DisplayName("[SPEC-003 R-2] 100MB 이하는 단일 업로드다")
  void singleUpToLimit() {
    assertThat(UploadPlan.of(SINGLE_MAX, SINGLE_MAX, PART).method())
        .isEqualTo(UploadPlan.Method.SINGLE);
  }

  @Test
  @DisplayName("[SPEC-003 R-3] 100MB를 넘으면 64MiB 조각으로 나눈 멀티파트다")
  void multipartAboveLimit() {
    UploadPlan plan = UploadPlan.of(SINGLE_MAX + 1, SINGLE_MAX, PART);

    assertThat(plan.method()).isEqualTo(UploadPlan.Method.MULTIPART);
    assertThat(plan.partSizeBytes()).isEqualTo(PART);
    assertThat(plan.partCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-003 R-3] 5GB는 80조각이다")
  void fiveGbIsEightyParts() {
    assertThat(UploadPlan.of(VideoFilePolicy.MAX_SIZE_BYTES, SINGLE_MAX, PART).partCount())
        .isEqualTo(80);
  }
}
