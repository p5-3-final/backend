package com.vlm.media.shared.api;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import java.util.Arrays;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** api-gateway가 검증한 토큰으로 채우는 사용자 헤더(계약 _common.yaml XUserId, XUserRoles). */
public final class UserHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";

  private UserHeaders() {}

  /** 쉼표로 이은 역할 중에 ADMIN이 있는지. */
  public static boolean isAdmin(@Nullable String roles) {
    return roles != null
        && Arrays.stream(roles.split(",")).map(String::trim).anyMatch("ADMIN"::equals);
  }

  public static UUID userId(@Nullable String value) {
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
