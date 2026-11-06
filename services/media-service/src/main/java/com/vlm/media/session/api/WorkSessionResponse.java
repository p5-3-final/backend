package com.vlm.media.session.api;

import com.vlm.media.shared.domain.WorkSession;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 계약 {@code WorkSession}. 외부에는 내부 PK가 아닌 public_id를 id로 노출한다. */
record WorkSessionResponse(
    UUID id,
    UUID ownerId,
    String name,
    @Nullable String site,
    @Nullable String area,
    Instant createdAt) {

  static WorkSessionResponse from(WorkSession session) {
    return new WorkSessionResponse(
        session.getPublicId(),
        session.getOwnerId(),
        session.getName(),
        session.getSite(),
        session.getArea(),
        session.getCreatedAt());
  }
}
