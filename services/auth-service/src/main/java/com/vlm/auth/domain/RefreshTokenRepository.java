package com.vlm.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 재발급 토큰 저장소. 구현은 infra 레이어에 있다. */
public interface RefreshTokenRepository {

  RefreshToken save(RefreshToken refreshToken);

  /** 같은 토큰으로 동시에 재발급하지 못하도록 행을 잠그고 조회한다. */
  Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

  /** 로그인 계열의 토큰을 모두 폐기한다. */
  void revokeFamily(UUID familyId, Instant now);
}
