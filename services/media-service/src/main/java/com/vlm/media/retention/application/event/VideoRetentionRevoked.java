package com.vlm.media.retention.application.event;

import java.time.Instant;
import java.util.UUID;

/** 계약 {@code video.retention.revoked.schema.json}(SPEC-018 R-18). */
public record VideoRetentionRevoked(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId,
    String retentionKey) {

  public static final String TYPE = "video.retention.revoked";

  public static VideoRetentionRevoked of(
      UUID sourceId, UUID ownerId, String retentionKey, Instant occurredAt) {
    return new VideoRetentionRevoked(
        UUID.randomUUID(), TYPE, 1, occurredAt, sourceId, ownerId, retentionKey);
  }
}
