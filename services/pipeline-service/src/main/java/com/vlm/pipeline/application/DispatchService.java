package com.vlm.pipeline.application;

import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.pipeline.config.DispatchProperties;
import com.vlm.pipeline.domain.*;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchService {
  private final DispatchRepository repository;
  private final OutboxRecorder outbox;
  private final DispatchProperties properties;
  private final JobExecutionService executions;

  /** 한 건 배정만 잠근다. Kafka I/O는 기존 Publisher의 별도 트랜잭션이다. */
  @Transactional
  public Optional<UUID> dispatchOne() {
    repository.lock();
    repository.ensureConfiguration(
        properties.userLimit(),
        properties.sourceLimit(),
        properties.globalLimit(),
        properties.startWindowMs());
    repository.expire();
    executions.refreshLive();
    var selected =
        repository.next(properties.userLimit(), properties.sourceLimit(), properties.globalLimit());
    if (selected.isEmpty()) return Optional.empty();
    var c = selected.get();
    UUID executionId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    var now = repository.now();
    var deadline = now.plusMillis(properties.startWindowMs());
    var request =
        new ChunkRequest(
            eventId,
            "chunk.requested",
            1,
            now,
            c.sourceId(),
            c.ownerId(),
            c.jobId(),
            c.chunkId(),
            executionId,
            executionId,
            c.normalizationId(),
            c.normalizedKey(),
            new ChunkRequest.Range(c.ownedStart(), c.ownedEnd()),
            new ChunkRequest.Range(c.processingStart(), c.processingEnd()),
            c.settings(),
            deadline);
    repository.createExecution(c, executionId, eventId, deadline);
    repository.occupy(c, executionId);
    repository.markDispatched(c);
    repository.advanceFairness(c.ownerId());
    outbox.record(eventId, "chunk.requested", c.sourceId(), request);
    repository.preserveTrace(eventId, c.traceparent());
    executions.refresh(c.jobId());
    return Optional.of(executionId);
  }
}
