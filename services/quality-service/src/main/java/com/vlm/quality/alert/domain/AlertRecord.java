package com.vlm.quality.alert.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * alert.raised 한 건에서 기록할 값.
 *
 * @param occurredAt 이 상태를 발행한 시각(envelope occurredAt)
 * @param dismissReason dismissed일 때만 값이 있다
 */
public record AlertRecord(
    UUID candidateId,
    UUID sourceId,
    UUID cameraId,
    String eventType,
    String severity,
    AlertStatus status,
    Instant occurredAt,
    @Nullable String dismissReason,
    List<String> evidenceFrameKeys) {

  public AlertRecord {
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
