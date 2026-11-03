package com.vlm.pipeline.domain;

import java.time.Instant;
import java.util.UUID;

/** 신규 backend 계약의 정수 밀리초 요청. 재발행은 이 payload를 바꾸지 않는다. */
public record ChunkRequest(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId,
    UUID jobId,
    UUID chunkId,
    UUID executionId,
    UUID requestId,
    UUID normalizationId,
    String normalizedKey,
    Range ownedRange,
    Range processingRange,
    AnalysisSettings processingProfile,
    Instant startBefore) {
  public ChunkRequest {
    if (!executionId.equals(requestId)
        || !eventType.equals("chunk.requested")
        || schemaVersion != 1
        || !startBefore.isAfter(occurredAt)
        || processingRange.startMs() > ownedRange.startMs()
        || processingRange.endMs() < ownedRange.endMs()
        || !normalizedKey.startsWith(
            "sources/" + sourceId + "/normalizations/" + normalizationId + "/")
        || normalizedKey.contains("..")
        || normalizedKey.contains("?")
        || normalizedKey.contains("#")) throw new IllegalArgumentException("Invalid chunk request");
  }

  public record Range(long startMs, long endMs) {
    public Range {
      if (startMs < 0 || endMs <= startMs) throw new IllegalArgumentException("Invalid range");
    }
  }
}
