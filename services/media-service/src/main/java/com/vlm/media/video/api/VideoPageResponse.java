package com.vlm.media.video.api;

import com.vlm.media.shared.api.VideoResponse;
import com.vlm.media.shared.domain.VideoPage;
import java.util.List;

/** 계약 {@code VideoPage}. */
record VideoPageResponse(List<VideoResponse> items, PageMeta page) {

  static VideoPageResponse from(VideoPage page) {
    return new VideoPageResponse(
        page.items().stream().map(VideoResponse::from).toList(),
        new PageMeta(page.page(), page.size(), page.totalElements(), page.totalPages()));
  }

  /** 계약 _common.yaml {@code PageMeta}. */
  record PageMeta(int page, int size, long totalElements, int totalPages) {}
}
