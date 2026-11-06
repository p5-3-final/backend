package com.vlm.media.shared.infra.persistence;

import com.vlm.media.shared.domain.WorkSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface WorkSessionJpaRepository extends JpaRepository<WorkSession, Long> {

  Optional<WorkSession> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<WorkSession> findWithLockByPublicId(UUID publicId);

  Page<WorkSession> findByOwnerId(UUID ownerId, Pageable pageable);
}
