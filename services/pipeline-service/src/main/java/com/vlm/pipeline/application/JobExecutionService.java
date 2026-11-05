package com.vlm.pipeline.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.common.error.BusinessException;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.pipeline.domain.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobExecutionService {
  private final DispatchRepository dispatch;
  private final JobExecutionRepository repository;
  private final OutboxRecorder outbox;

  @Transactional
  public void refresh(UUID jobId) {
    dispatch.lock();
    repository
        .refresh(jobId)
        .ifPresent(
            snapshot -> {
              UUID eventId = UUID.randomUUID();
              ObjectNode event = snapshot.deepCopy();
              event.put("eventId", eventId.toString());
              event.put("eventType", "job.status");
              event.put("schemaVersion", 1);
              String now = Instant.now().toString();
              event.put("occurredAt", now);
              event.put("producedAt", now);
              outbox.record(
                  eventId,
                  "job.status",
                  UUID.fromString(snapshot.path("sourceId").asText()),
                  event);
            });
  }

  @Transactional
  public void refreshLive() {
    dispatch.lock();
    for (UUID job : repository.live()) refresh(job);
  }

  @Transactional
  public int reconcile(int batchSize) {
    if (batchSize < 1 || batchSize > 1000)
      throw new IllegalArgumentException("Invalid reconciliation batch");
    dispatch.lock();
    var jobs = repository.due(batchSize);
    for (UUID job : jobs) refresh(job);
    return jobs.size();
  }

  @Transactional
  public ObjectNode status(UUID jobId, UUID ownerId) {
    if (!repository.owned(jobId, ownerId))
      throw new BusinessException(JobExecutionError.JOB_NOT_FOUND);
    refresh(jobId);
    return repository
        .find(jobId, ownerId)
        .orElseThrow(() -> new BusinessException(JobExecutionError.JOB_NOT_FOUND));
  }

  @Transactional
  public void refreshExecution(UUID executionId) {
    dispatch.lock();
    repository.jobForExecution(executionId).ifPresent(this::refresh);
  }
}
