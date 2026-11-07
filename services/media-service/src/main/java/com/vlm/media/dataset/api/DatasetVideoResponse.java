package com.vlm.media.dataset.api;

import com.vlm.media.shared.domain.Video;
import java.util.UUID;

/** 계약 {@code DatasetVideo}. 상태는 {@code VideoResponse.status}와 같은 값이다. */
record DatasetVideoResponse(UUID videoId, String status, long sizeBytes) {

  static DatasetVideoResponse from(Video video) {
    return new DatasetVideoResponse(
        video.getPublicId(), video.getStatus().name(), video.getSizeBytes());
  }
}
