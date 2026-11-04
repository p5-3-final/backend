package com.vlm.media.video.application.event;

import com.vlm.media.shared.domain.Video;
import java.time.Instant;
import java.util.UUID;

/** 계약 {@code video.deleted.schema.json}(SPEC-003 R-21). */
public record VideoDeleted(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId) {

  public static final String TYPE = "video.deleted";

  public static VideoDeleted of(Video video, Instant occurredAt) {
    return new VideoDeleted(
        UUID.randomUUID(), TYPE, 1, occurredAt, video.getPublicId(), video.getOwnerId());
  }
}
