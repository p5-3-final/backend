package com.vlm.media.shared.infra.persistence;

import com.vlm.media.shared.domain.WorkSession;
import com.vlm.media.shared.domain.WorkSessionPage;
import com.vlm.media.shared.domain.WorkSessionRepository;
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
class WorkSessionRepositoryAdapter implements WorkSessionRepository {

  // 같은 시각에 만든 세션도 순서가 페이지마다 흔들리지 않도록 id로 한 번 더 정렬한다.
  private static final Sort NEWEST_FIRST = Sort.by(Direction.DESC, "createdAt", "id");

  private final WorkSessionJpaRepository jpaRepository;

  @Override
  public WorkSession save(WorkSession session) {
    return jpaRepository.save(session);
  }

  @Override
  public Optional<WorkSession> findByPublicId(UUID publicId) {
    return jpaRepository.findByPublicId(publicId);
  }

  @Override
  public Optional<WorkSession> findByPublicIdForUpdate(UUID publicId) {
    return jpaRepository.findWithLockByPublicId(publicId);
  }

  @Override
  public WorkSessionPage findByOwner(UUID ownerId, int page, int size) {
    Page<WorkSession> result =
        jpaRepository.findByOwnerId(ownerId, PageRequest.of(page, size, NEWEST_FIRST));
    return new WorkSessionPage(result.getContent(), page, size, result.getTotalElements());
  }
}
