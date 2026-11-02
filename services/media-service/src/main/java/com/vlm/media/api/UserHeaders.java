package com.vlm.media.api;

import com.vlm.common.error.BusinessException;
import com.vlm.media.domain.MediaErrorCode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** api-gateway가 검증한 토큰으로 채우는 사용자 헤더(계약 _common.yaml XUserId, XUserRoles). */
final class UserHeaders {

  static final String USER_ID = "X-User-Id";
  static final String USER_ROLES = "X-User-Roles";

  private UserHeaders() {}

  static UUID userId(@Nullable String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(MediaErrorCode.TOKEN_MISSING);
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          MediaErrorCode.TOKEN_INVALID, MediaErrorCode.TOKEN_INVALID.defaultMessage(), e);
    }
  }
}
