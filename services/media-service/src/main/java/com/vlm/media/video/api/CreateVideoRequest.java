package com.vlm.media.video.api;

import com.vlm.media.shared.domain.SessionRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 계약 {@code CreateVideoRequest}. 크기 범위와 형식은 오류 코드를 따로 주려고 도메인 정책에서 검사한다(SPEC-003 R-4, R-5). 세션과 역할은
 * 함께 보낸다(SPEC-017 R-6, R-8).
 */
record CreateVideoRequest(
    @NotBlank @Size(max = 255) String fileName,
    @NotNull Long sizeBytes,
    @NotBlank String contentType,
    @Nullable UUID sessionId,
    @Nullable SessionRole role) {

  // SPEC-017 R-8: 세션과 역할은 함께 있거나 함께 없어야 한다. 계약 스키마로는 하위호환 검사 때문에 강제하지 않는다.
  @AssertTrue
  boolean isSessionComplete() {
    return (sessionId == null) == (role == null);
  }
}
