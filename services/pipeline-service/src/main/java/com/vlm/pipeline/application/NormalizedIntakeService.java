package com.vlm.pipeline.application;

import com.vlm.pipeline.domain.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NormalizedIntakeService {
  private final NormalizedReceiptRepository receipts;
  private final PlanningService planning;
  private final JobExecutionService executions;
  private final com.vlm.pipeline.config.NormalizedIntakeProperties properties;

  /** 수신 완료 표시와 계획은 같은 트랜잭션이다. 배정·요청 Outbox는 생성하지 않는다. */
  @Transactional
  public IntakeResult receive(
      NormalizedVideo event, AnalysisSettings settings, @Nullable String traceparent) {
    receipts.lockEvent(event.eventId());
    var received = receipts.findReceipt(event);
    if (received.isPresent()) return received.get();
    if (event.ready() && event.durationMs() > properties.maxDurationMs())
      throw new IllegalArgumentException("Normalized video exceeds configured duration limit");
    receipts.lockNormalization(event.normalizationId());
    receipts.ensureNormalization(event);
    java.util.UUID jobId = null;
    if (event.ready()) {
      receipts.ensureSettings(settings);
      var existing = receipts.findJob(event.normalizationId(), settings.version());
      if (existing.isPresent()) jobId = existing.get();
      else {
        jobId = java.util.UUID.randomUUID();
        planning.create(settings.plan(jobId, event));
        receipts.linkJob(event.normalizationId(), settings.version(), jobId);
      }
    }
    receipts.saveReceipt(event, jobId, traceparent);
    if (jobId != null) executions.refresh(jobId);
    return new IntakeResult(event.eventId(), jobId, event.ready() ? "READY" : "REJECTED");
  }
}
