package com.vlm.auth.api;

/** 계약 {@code TokenResponse}. */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

  static TokenResponse bearer(String accessToken, long expiresIn) {
    return new TokenResponse(accessToken, "Bearer", expiresIn);
  }
}
