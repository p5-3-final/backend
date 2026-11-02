package com.vlm.auth.api;

import com.vlm.auth.application.MeService;
import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.common.error.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class MeController {

  private final MeService meService;

  // X-User-Id는 api-gateway가 검증한 토큰의 sub로 채운다(계약 _common.yaml XUserId).
  @GetMapping("/api/v1/auth/me")
  UserResponse me(@RequestHeader(value = "X-User-Id", required = false) @Nullable String userId) {
    if (userId == null || userId.isBlank()) {
      throw new BusinessException(AuthErrorCode.TOKEN_MISSING);
    }
    return UserResponse.from(meService.find(parse(userId)));
  }

  private static UUID parse(String userId) {
    try {
      return UUID.fromString(userId);
    } catch (IllegalArgumentException e) {
      throw new BusinessException(
          AuthErrorCode.TOKEN_INVALID, AuthErrorCode.TOKEN_INVALID.defaultMessage(), e);
    }
  }
}
