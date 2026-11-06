package com.vlm.quality.review.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 큐에 넣을 새 검토 항목. 상태는 항상 UNASSIGNED로 시작한다(R-1). */
public record NewReviewItem(
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
    Instant createdAt) {

  public NewReviewItem {
    reviewReasons = List.copyOf(reviewReasons);
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
