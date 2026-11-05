package com.vlm.common.web;

import com.vlm.common.error.BusinessException;
import java.util.Arrays;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * api-gateway가 검증한 토큰으로 채우는 사용자 헤더(계약 _common.yaml XUserId, XUserRoles).
 *
 * <p>헤더가 없거나 형식이 틀리면 게이트웨이를 거치지 않은 요청이므로 토큰 오류(401)로 거절한다.
 */
public final class UserHeaders {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";

  private UserHeaders() {}

  /**
   * 사용자 ID 헤더를 읽는다.
   *
   * @throws BusinessException 헤더가 없으면 {@link CommonErrorCode#TOKEN_MISSING}, UUID가 아니면 {@link
   *     CommonErrorCode#TOKEN_INVALID}
   */
  public static UUID userId(@Nullable String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.TOKEN_MISSING);
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          CommonErrorCode.TOKEN_INVALID, CommonErrorCode.TOKEN_INVALID.defaultMessage(), e);
    }
  }

  /** 쉼표로 이은 역할 중에 ADMIN이 있는지. */
  public static boolean isAdmin(@Nullable String roles) {
    return roles != null
        && Arrays.stream(roles.split(",")).map(String::trim).anyMatch("ADMIN"::equals);
  }
}
