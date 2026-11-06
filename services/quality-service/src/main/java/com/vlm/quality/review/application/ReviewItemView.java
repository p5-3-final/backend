package com.vlm.quality.review.application;

import com.vlm.quality.review.domain.ReviewItem;
import java.net.URI;
import java.util.List;

/** 응답용 검토 항목과 근거 프레임 서명 주소(경로와 같은 순서). */
public record ReviewItemView(ReviewItem item, List<URI> evidenceFrameUrls) {

  public ReviewItemView {
    evidenceFrameUrls = List.copyOf(evidenceFrameUrls);
  }
}
