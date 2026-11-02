package com.vlm.media.infra.persistence;

import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class VideoRepositoryAdapter implements VideoRepository {

  private final VideoJpaRepository jpaRepository;

  @Override
  public Video save(Video video) {
    return jpaRepository.save(video);
  }

  @Override
  public Optional<Video> findByPublicId(UUID publicId) {
    return jpaRepository.findByPublicId(publicId);
  }

  @Override
  public Optional<Video> findByPublicIdForUpdate(UUID publicId) {
    return jpaRepository.findWithLockByPublicId(publicId);
  }
}
