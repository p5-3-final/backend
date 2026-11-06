package com.vlm.media.video.api;

import com.vlm.common.web.PageMeta;
import com.vlm.media.shared.api.VideoResponse;
import com.vlm.media.shared.domain.VideoPage;
import java.util.List;

/** 계약 {@code VideoPage}. */
record VideoPageResponse(List<VideoResponse> items, PageMeta page) {

  static VideoPageResponse from(VideoPage page) {
    return new VideoPageResponse(
        page.items().stream().map(VideoResponse::from).toList(),
        PageMeta.of(page.page(), page.size(), page.totalElements()));
  }
}
