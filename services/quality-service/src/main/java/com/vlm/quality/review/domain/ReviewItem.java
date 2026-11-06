package com.vlm.quality.review.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 검토 큐 항목.
 *
 * @param id 내부 행 ID(Golden Set 초안이 참조)
 * @param analysisJson 판정 결과 VLMAnalysis JSON 그대로. alert_dismissed 항목은 {@code {}}다
 */
public record ReviewItem(
    long id,
    UUID itemId,
    UUID eventId,
    UUID sourceId,
    UUID jobId,
    String eventType,
    long startMs,
    long endMs,
    String judgmentMethod,
    String taxonomyVersion,
    ReviewReason reason,
    List<String> reviewReasons,
    List<String> evidenceFrameKeys,
    String analysisJson,
    ReviewStatus status,
    @Nullable UUID assigneeId,
    @Nullable Instant assignedAt,
    @Nullable ReviewLabel label,
    @Nullable Instant labeledAt,
    Instant createdAt) {

  public ReviewItem {
    reviewReasons = List.copyOf(reviewReasons);
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
