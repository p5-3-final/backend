package com.vlm.pipeline.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 배정과 회수의 상태 변경은 application 트랜잭션 안에서 호출한다. */
public interface DispatchRepository {
  void lock();

  void ensureConfiguration(int userLimit, int sourceLimit, int globalLimit, long startWindowMs);

  Instant now();

  Optional<DispatchCandidate> next(int userLimit, int sourceLimit, int globalLimit);

  void createExecution(
      DispatchCandidate candidate, UUID executionId, UUID eventId, Instant startBefore);

  void occupy(DispatchCandidate candidate, UUID executionId);

  void markDispatched(DispatchCandidate candidate);

  void advanceFairness(UUID ownerId);

  void preserveTrace(UUID eventId, @Nullable String traceparent);

  int expire();

  void confirmStopped(UUID executionId, UUID confirmationId, String evidence);

  long recoveryCount();
}
