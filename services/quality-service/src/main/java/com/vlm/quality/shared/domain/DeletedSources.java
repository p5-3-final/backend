package com.vlm.quality.shared.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 삭제된 영상 흔적(계약 video.deleted "소비자는 삭제 흔적을 남긴다").
 *
 * <p>video.deleted가 event.analyzed·alert.raised보다 먼저 올 수 있어, 늦게 온 이벤트로 지운 데이터를 다시 만들지 않으려고 둔다.
 */
public interface DeletedSources {

  /** 흔적을 남긴다. 이미 있으면 그대로 둔다. */
  void record(UUID sourceId, Instant deletedAt);

  boolean isDeleted(UUID sourceId);
}
