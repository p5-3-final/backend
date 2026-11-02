package com.vlm.media.infra.persistence;

import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface VideoJpaRepository extends JpaRepository<Video, Long> {

  Optional<Video> findByPublicId(UUID publicId);

  Page<Video> findByOwnerIdAndStatusNot(UUID ownerId, VideoStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Video> findWithLockByPublicId(UUID publicId);
}
