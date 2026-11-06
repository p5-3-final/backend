package com.vlm.media.session.api;

import com.vlm.media.session.application.WorkSessionService.SessionDetail;
import com.vlm.media.shared.api.VideoResponse;
import com.vlm.media.shared.domain.WorkSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 계약 {@code WorkSessionDetail}. */
record WorkSessionDetailResponse(
    UUID id,
    UUID ownerId,
    String name,
    @Nullable String site,
    @Nullable String area,
    Instant createdAt,
    List<VideoResponse> videos) {

  static WorkSessionDetailResponse from(SessionDetail detail) {
    WorkSession session = detail.session();
    return new WorkSessionDetailResponse(
        session.getPublicId(),
        session.getOwnerId(),
        session.getName(),
        session.getSite(),
        session.getArea(),
        session.getCreatedAt(),
        detail.videos().stream().map(VideoResponse::from).toList());
  }
}
