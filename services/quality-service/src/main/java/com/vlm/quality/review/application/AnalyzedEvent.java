package com.vlm.quality.review.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 검토 큐가 보는 event.analyzed 필드.
 *
 * @param verificationStatus analysis.verificationStatus: confirmed·rejected·unverified·unknown
 * @param unverifiedReason unverified일 때 이유(v5.1). 아니면 null
 * @param analysisJson analysis 그대로(JSON)
 */
public record AnalyzedEvent(
    UUID eventId,
    UUID sourceId,
    UUID jobId,
    String candidateEventType,
    long startMs,
    long endMs,
    String judgmentMethod,
    String taxonomyVersion,
    boolean reviewRequired,
    List<String> reviewReasons,
    String verificationStatus,
    @Nullable String unverifiedReason,
    List<String> evidenceFrameKeys,
    String analysisJson) {

  public AnalyzedEvent {
    reviewReasons = List.copyOf(reviewReasons);
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
