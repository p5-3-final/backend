package com.vlm.media.infra.persistence;

import com.vlm.media.domain.Video;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface VideoJpaRepository extends JpaRepository<Video, Long> {

  Optional<Video> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Video> findWithLockByPublicId(UUID publicId);
}
