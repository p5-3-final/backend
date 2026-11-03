package com.vlm.pipeline.domain;

import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 수신·정규화 불변성·설정 및 계획 연결의 저장 경계. 호출자의 트랜잭션을 사용한다. */
public interface NormalizedReceiptRepository {
  void lockEvent(UUID id);

  void lockNormalization(UUID id);

  Optional<IntakeResult> findReceipt(NormalizedVideo event);

  void ensureNormalization(NormalizedVideo event);

  void ensureSettings(AnalysisSettings settings);

  Optional<UUID> findJob(UUID normalizationId, String settingsVersion);

  void linkJob(UUID normalizationId, String settingsVersion, UUID jobId);

  void saveReceipt(NormalizedVideo event, @Nullable UUID jobId, @Nullable String traceparent);
}
