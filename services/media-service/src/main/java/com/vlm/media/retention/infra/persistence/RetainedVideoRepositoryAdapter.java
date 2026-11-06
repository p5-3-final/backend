package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetainedVideo;
import com.vlm.media.retention.domain.RetainedVideoPage;
import com.vlm.media.retention.domain.RetainedVideoRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RetainedVideoRepositoryAdapter implements RetainedVideoRepository {

  // 같은 시각에 지운 영상도 순서가 페이지마다 흔들리지 않도록 id로 한 번 더 정렬한다.
  private static final Sort NEWEST_FIRST = Sort.by(Direction.DESC, "deletedAt", "id");

  private final RetainedVideoJpaRepository jpaRepository;

  @Override
  public RetainedVideo save(RetainedVideo retainedVideo) {
    return jpaRepository.save(retainedVideo);
  }

  @Override
  public RetainedVideoPage findByOwner(UUID ownerId, int page, int size) {
    Page<RetainedVideo> result =
        jpaRepository.findByOwnerId(ownerId, PageRequest.of(page, size, NEWEST_FIRST));
    return new RetainedVideoPage(result.getContent(), page, size, result.getTotalElements());
  }

  @Override
  public int deleteExpired(Instant now, int limit) {
    return jpaRepository.deleteExpired(now, limit);
  }
}
