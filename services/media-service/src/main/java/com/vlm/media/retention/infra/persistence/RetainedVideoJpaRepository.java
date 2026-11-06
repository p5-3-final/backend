package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetainedVideo;
import org.springframework.data.jpa.repository.JpaRepository;

interface RetainedVideoJpaRepository extends JpaRepository<RetainedVideo, Long> {}
