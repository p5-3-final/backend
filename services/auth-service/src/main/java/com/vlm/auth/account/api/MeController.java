package com.vlm.auth.account.api;

import com.vlm.auth.account.application.MeService;
import com.vlm.common.web.UserHeaders;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class MeController {

  private final MeService meService;

  @GetMapping("/api/v1/auth/me")
  UserResponse me(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId) {
    return UserResponse.from(meService.find(UserHeaders.userId(userId)));
  }
}
