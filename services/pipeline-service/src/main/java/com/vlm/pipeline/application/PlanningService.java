package com.vlm.pipeline.application;

import com.vlm.pipeline.domain.*;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanningService {
  private final PlanRepository repository;

  /** 같은 계획 ID는 저장된 결과를 재사용한다. */
  @Transactional
  public JobPlan create(PlanInput input) {
    repository.lockPlan(input.planId());
    var existing = repository.find(input.planId());
    if (existing.isPresent()) {
      if (!existing.get().input().equals(input))
        throw new com.vlm.common.error.BusinessException(PlanningError.INPUT_CONFLICT);
      return existing.get();
    }
    repository.lockSource(input.sourceId());
    if (repository.hasActiveJob(input.sourceId()))
      throw new com.vlm.common.error.BusinessException(PlanningError.ACTIVE_JOB_EXISTS);
    var plan = new JobPlan(input, "PLANNED", ChunkPlanner.calculate(input));
    repository.save(plan);
    return plan;
  }

  /** 계획과 청크를 하나의 읽기 트랜잭션에서 복원한다. */
  @Transactional(readOnly = true)
  public Optional<JobPlan> find(UUID planId) {
    return repository.find(planId);
  }
}
