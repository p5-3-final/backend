package com.vlm.media.retention.api;

import com.vlm.common.web.PageMeta;
import com.vlm.media.retention.domain.RetainedVideo;
import com.vlm.media.retention.domain.RetainedVideoPage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 계약 {@code RetainedVideoPage}. 최소 기록이라 영상 ID와 시각만 담는다(ADR-0011). */
record RetainedVideoPageResponse(List<Item> items, PageMeta page) {

  static RetainedVideoPageResponse from(RetainedVideoPage result) {
    return new RetainedVideoPageResponse(
        result.items().stream().map(Item::from).toList(),
        PageMeta.of(result.page(), result.size(), result.totalElements()));
  }

  /** 계약 {@code RetainedVideo}. */
  record Item(UUID videoId, Instant deletedAt, Instant retainUntil) {

    static Item from(RetainedVideo record) {
      return new Item(record.getSourceId(), record.getDeletedAt(), record.getRetainUntil());
    }
  }
}
