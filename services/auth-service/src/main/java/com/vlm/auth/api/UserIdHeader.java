package com.vlm.auth.api;

import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.common.error.BusinessException;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** api-gateway가 검증한 토큰의 sub로 채우는 {@code X-User-Id} 헤더(계약 _common.yaml XUserId). */
final class UserIdHeader {

  static final String NAME = "X-User-Id";

  private UserIdHeader() {}

  static UUID parse(@Nullable String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(AuthErrorCode.TOKEN_MISSING);
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          AuthErrorCode.TOKEN_INVALID, AuthErrorCode.TOKEN_INVALID.defaultMessage(), e);
    }
  }
}
