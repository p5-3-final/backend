package com.vlm.auth.api;

import com.vlm.auth.application.LogInCommand;
import com.vlm.auth.application.LogInResult;
import com.vlm.auth.application.LogInService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class LogInController {

  private final LogInService logInService;

  @PostMapping("/api/v1/auth/login")
  ResponseEntity<TokenResponse> logIn(
      @Valid @RequestBody LogInRequest request, HttpServletRequest servletRequest) {
    LogInResult result =
        logInService.logIn(
            new LogInCommand(request.email(), request.password(), servletRequest.getRemoteAddr()));
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookies.issue(result.refreshToken()).toString())
        .body(TokenResponse.bearer(result.accessToken(), result.expiresIn()));
  }
}
