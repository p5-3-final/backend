package com.vlm.quality.golden.domain;

import com.vlm.quality.shared.domain.ReviewLabel;
import java.util.List;
import java.util.UUID;

/** 확정할 초안 항목과, 매니페스트를 채울 검토 항목 값(R-14). */
public record FreezeEntry(
    UUID entryId,
    UUID sourceId,
    UUID eventId,
    String candidateEventType,
    String judgmentMethod,
    long startMs,
    long endMs,
    ReviewLabel label,
    String analysisJson,
    List<String> evidenceFrameKeys,
    String taxonomyVersion) {

  public FreezeEntry {
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
