package com.vlm.media.infra.persistence;

import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoPage;
import com.vlm.media.domain.VideoRepository;
import com.vlm.media.domain.VideoStatus;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class VideoRepositoryAdapter implements VideoRepository {

  private static final Sort NEWEST_FIRST = Sort.by(Direction.DESC, "createdAt", "id");

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
  public VideoPage findVisibleByOwner(UUID ownerId, int page, int size) {
    // 같은 시각에 만든 영상도 순서가 페이지마다 흔들리지 않도록 id로 한 번 더 정렬한다.
    Page<Video> result =
        jpaRepository.findByOwnerIdAndStatusNot(
            ownerId, VideoStatus.DELETED, PageRequest.of(page, size, NEWEST_FIRST));
    return new VideoPage(result.getContent(), page, size, result.getTotalElements());
  }

  @Override
  public Optional<Video> findByPublicIdForUpdate(UUID publicId) {
    return jpaRepository.findWithLockByPublicId(publicId);
  }
}
