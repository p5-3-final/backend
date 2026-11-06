package com.vlm.quality.review.application;

import java.util.List;

/** 검토 항목 한 쪽. */
public record ReviewItemViewPage(List<ReviewItemView> items, long totalElements) {

  public ReviewItemViewPage {
    items = List.copyOf(items);
  }
}
