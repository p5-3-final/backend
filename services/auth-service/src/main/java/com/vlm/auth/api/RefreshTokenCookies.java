package com.vlm.auth.api;

import com.vlm.auth.domain.RefreshToken;
import org.springframework.http.ResponseCookie;

/** 재발급 토큰 쿠키. 속성은 계약 {@code auth-service.yaml}의 SetRefreshTokenCookie와 같다. */
final class RefreshTokenCookies {

  static final String NAME = "refresh_token";
  // 재발급·로그아웃 요청에만 쿠키가 실리도록 경로를 좁힌다.
  private static final String PATH = "/api/v1/auth";

  private RefreshTokenCookies() {}

  static ResponseCookie issue(String rawToken) {
    return ResponseCookie.from(NAME, rawToken)
        .path(PATH)
        .maxAge(RefreshToken.TIME_TO_LIVE)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .build();
  }
}
