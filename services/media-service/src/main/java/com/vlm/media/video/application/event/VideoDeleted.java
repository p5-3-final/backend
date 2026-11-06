package com.vlm.media.video.application.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vlm.media.retention.application.Retention;
import com.vlm.media.shared.domain.Video;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 계약 {@code video.deleted.schema.json}(SPEC-003 R-21). 보존에 동의한 영상이면 {@code retention}을 담는다(SPEC-018
 * R-8).
 */
public record VideoDeleted(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable Retention retention) {

  public static final String TYPE = "video.deleted";

  public static VideoDeleted of(Video video, Instant occurredAt, @Nullable Retention retention) {
    return new VideoDeleted(
        UUID.randomUUID(), TYPE, 1, occurredAt, video.getPublicId(), video.getOwnerId(), retention);
  }
}
