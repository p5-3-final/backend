package com.vlm.media.video.application.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vlm.media.shared.domain.SessionRole;
import com.vlm.media.shared.domain.Video;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 계약 {@code video.uploaded.schema.json}(SPEC-003 R-21). 세션에 속한 영상이면 {@code sessionId}·{@code role}을
 * 담고, 아니면 두 필드를 뺀다(SPEC-017 R-11, R-12).
 */
public record VideoUploaded(
    UUID eventId,
    String eventType,
    int schemaVersion,
    Instant occurredAt,
    UUID sourceId,
    UUID ownerId,
    String storageKey,
    long sizeBytes,
    String contentType,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable UUID sessionId,
    @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String role) {

  public static final String TYPE = "video.uploaded";

  public static VideoUploaded of(Video video, Instant occurredAt) {
    SessionRole role = video.getSessionRole();
    return new VideoUploaded(
        UUID.randomUUID(),
        TYPE,
        1,
        occurredAt,
        video.getPublicId(),
        video.getOwnerId(),
        video.getStorageKey(),
        video.getSizeBytes(),
        video.getContentType(),
        video.getSessionId(),
        role == null ? null : role.name());
  }
}
