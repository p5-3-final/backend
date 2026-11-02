package com.vlm.auth.api;

import com.vlm.auth.application.IssuedTokens;
import com.vlm.auth.application.TokenRefreshService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class RefreshController {

  private final TokenRefreshService tokenRefreshService;

  @PostMapping("/api/v1/auth/refresh")
  ResponseEntity<TokenResponse> refresh(
      @CookieValue(name = RefreshTokenCookies.NAME, required = false)
          @Nullable String refreshToken) {
    IssuedTokens tokens = tokenRefreshService.refresh(refreshToken);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookies.issue(tokens.refreshToken()).toString())
        .body(TokenResponse.bearer(tokens.accessToken(), tokens.expiresIn()));
  }
}
