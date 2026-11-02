package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.RefreshToken;
import com.vlm.auth.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

  private final RefreshTokenJpaRepository jpaRepository;

  @Override
  public RefreshToken save(RefreshToken refreshToken) {
    return jpaRepository.save(refreshToken);
  }
}
