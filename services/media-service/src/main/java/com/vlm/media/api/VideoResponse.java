package com.vlm.media.api;

import com.vlm.media.domain.Video;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 계약 {@code VideoResponse}. 외부에는 내부 PK가 아닌 public_id를 id로 노출한다. */
record VideoResponse(
    UUID id,
    UUID ownerId,
    String fileName,
    long sizeBytes,
    String contentType,
    String status,
    Instant createdAt,
    @Nullable Instant uploadedAt) {

  static VideoResponse from(Video video) {
    return new VideoResponse(
        video.getPublicId(),
        video.getOwnerId(),
        video.getFileName(),
        video.getSizeBytes(),
        video.getContentType(),
        video.getStatus().name(),
        video.getCreatedAt(),
        video.getUploadedAt());
  }
}
