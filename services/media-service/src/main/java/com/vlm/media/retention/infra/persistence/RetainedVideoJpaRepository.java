package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetainedVideo;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RetainedVideoJpaRepository extends JpaRepository<RetainedVideo, Long> {

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
