package com.vlm.media.video.api;

import com.vlm.media.shared.domain.RejectReason;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoStatus;
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
    @Nullable Instant uploadedAt,
    @Nullable Double durationSec,
    @Nullable Integer estimatedProcessingSec,
    @Nullable RejectReason rejectReason) {

  static VideoResponse from(Video video) {
    // SPEC-007 R-21: 길이·예상 시간은 READY, 거절 사유는 REJECTED일 때만 보인다.
    boolean ready = video.getStatus() == VideoStatus.READY;
    Long durationMs = ready ? video.getDurationMs() : null;
    return new VideoResponse(
        video.getPublicId(),
        video.getOwnerId(),
        video.getFileName(),
        video.getSizeBytes(),
        video.getContentType(),
        video.getStatus().name(),
        video.getCreatedAt(),
        video.getUploadedAt(),
        durationMs == null ? null : durationMs / 1000.0,
        ready ? video.getEstimatedProcessingSec() : null,
        video.getStatus() == VideoStatus.REJECTED ? video.getRejectReason() : null);
  }
}
