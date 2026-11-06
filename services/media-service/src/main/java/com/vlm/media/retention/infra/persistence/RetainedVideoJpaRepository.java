package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetainedVideo;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RetainedVideoJpaRepository extends JpaRepository<RetainedVideo, Long> {

  Page<RetainedVideo> findByOwnerId(UUID ownerId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<RetainedVideo> findWithLockBySourceId(UUID sourceId);

  @Modifying
  @Query(
      value =
          """
          DELETE FROM retained_videos
          WHERE id IN (
              SELECT id FROM retained_videos
              WHERE retain_until < :now
              ORDER BY retain_until
              LIMIT :limit
              FOR UPDATE SKIP LOCKED)
          """,
      nativeQuery = true)
  int deleteExpired(@Param("now") Instant now, @Param("limit") int limit);
}
