package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RefreshToken> findWithLockByTokenHash(String tokenHash);

  // 벌크 UPDATE는 @UpdateTimestamp를 거치지 않으므로 updatedAt을 직접 바꾼다.
  @Modifying(flushAutomatically = true)
  @Query(
      "UPDATE RefreshToken t SET t.revokedAt = :now, t.updatedAt = :now"
          + " WHERE t.familyId = :familyId AND t.revokedAt IS NULL")
  void revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
