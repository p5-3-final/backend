package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetainedVideo;
import com.vlm.media.retention.domain.RetainedVideoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RetainedVideoRepositoryAdapter implements RetainedVideoRepository {

  private final RetainedVideoJpaRepository jpaRepository;

  @Override
  public RetainedVideo save(RetainedVideo retainedVideo) {
    return jpaRepository.save(retainedVideo);
  }
}
