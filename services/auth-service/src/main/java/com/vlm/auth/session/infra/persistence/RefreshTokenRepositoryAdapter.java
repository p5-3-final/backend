package com.vlm.auth.session.infra.persistence;

import com.vlm.auth.session.domain.RefreshToken;
import com.vlm.auth.session.domain.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

  @Override
  public Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash) {
    return jpaRepository.findWithLockByTokenHash(tokenHash);
  }

  @Override
  public void revokeFamily(UUID familyId, Instant now) {
    jpaRepository.revokeFamily(familyId, now);
  }
}
