package com.vlm.auth.api;

import com.vlm.auth.domain.User;
import java.util.List;
import java.util.UUID;

/** 계약 {@code UserResponse}. 외부에는 내부 PK가 아닌 public_id를 id로 노출한다. */
public record UserResponse(UUID id, String email, String nickname, List<String> roles) {

  static UserResponse from(User user) {
    return new UserResponse(
        user.getPublicId(), user.getEmail(), user.getNickname(), List.of(user.getRole().name()));
  }
}
