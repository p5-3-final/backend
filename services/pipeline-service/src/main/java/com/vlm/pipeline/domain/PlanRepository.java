package com.vlm.pipeline.domain;

import java.util.Optional;
import java.util.UUID;

/** 계획 영속성과 프로세스 간 중복 방지 경계. 잠금은 호출 트랜잭션 종료까지 유지한다. */
public interface PlanRepository {
  void lockPlan(UUID planId);

  void lockSource(UUID sourceId);

  Optional<JobPlan> find(UUID planId);

  boolean hasActiveJob(UUID sourceId);

  void save(JobPlan plan);
}
