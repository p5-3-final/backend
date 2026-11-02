package com.vlm.auth.domain;

/** 재발급 토큰 저장소. 구현은 infra 레이어에 있다. */
public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken refreshToken);
}
