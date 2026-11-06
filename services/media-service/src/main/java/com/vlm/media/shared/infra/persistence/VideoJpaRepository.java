package com.vlm.media.shared.infra.persistence;

import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VideoJpaRepository extends JpaRepository<Video, Long> {

  Optional<Video> findByPublicId(UUID publicId);

  Page<Video> findByOwnerIdAndStatusNot(UUID ownerId, VideoStatus status, Pageable pageable);

  List<Video> findBySessionIdAndStatusNot(UUID sessionId, VideoStatus status);

  long countBySessionIdAndStatusNot(UUID sessionId, VideoStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Video> findWithLockByPublicId(UUID publicId);

  @Query(
      value =
          """
          SELECT * FROM videos
          WHERE storage_cleaned_at IS NULL AND status IN ('EXPIRED', 'DELETED')
          ORDER BY id
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<Video> lockStorageCleanupTargets(@Param("limit") int limit);

  @Query(
      value =
          """
          SELECT * FROM videos
          WHERE status = 'UPLOADING' AND expires_at < :now
          ORDER BY expires_at
          LIMIT :limit
          FOR UPDATE SKIP LOCKED
          """,
      nativeQuery = true)
  List<Video> lockExpiredUploads(@Param("now") Instant now, @Param("limit") int limit);
}
