package com.vlm.auth.session.api;

import com.vlm.auth.session.application.LogOutService;
import com.vlm.auth.shared.api.UserIdHeader;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class LogOutController {

  private final LogOutService logOutService;

  @PostMapping("/api/v1/auth/logout")
  ResponseEntity<Void> logOut(
      @RequestHeader(value = UserIdHeader.NAME, required = false) @Nullable String userId,
      @CookieValue(name = RefreshTokenCookies.NAME, required = false)
          @Nullable String refreshToken) {
    logOutService.logOut(UserIdHeader.parse(userId), refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookies.clear().toString())
        .build();
  }
}
