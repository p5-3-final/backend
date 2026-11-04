package com.vlm.auth.domain;

/** 접근 토큰 발급. 형식은 docs 레포 {@code service-contracts/auth/access-token.md}. 구현은 infra 레이어에 있다. */
public interface AccessTokenIssuer {

  IssuedAccessToken issue(User user);

  /**
   * 발급한 접근 토큰.
   *
   * @param value 서명된 JWT
   * @param expiresInSeconds 만료까지 남은 초
   */
  record IssuedAccessToken(String value, long expiresInSeconds) {}
}
