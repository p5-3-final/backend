package com.vlm.media.application.event;

import com.vlm.media.domain.Video;
import java.time.Instant;
import java.util.UUID;

/** 계약 {@code video.uploaded.schema.json}(SPEC-003 R-21). */
public record VideoUploaded(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId,
    String storageKey,
    long sizeBytes,
    String contentType) {

  public static final String TYPE = "video.uploaded";

  public static VideoUploaded of(Video video, Instant occurredAt) {
    return new VideoUploaded(
        UUID.randomUUID(),
        TYPE,
        1,
        occurredAt,
        video.getPublicId(),
        video.getOwnerId(),
        video.getStorageKey(),
        video.getSizeBytes(),
        video.getContentType());
  }
}
