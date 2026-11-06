package com.vlm.quality.review.domain;

import java.util.List;

/** 검토 항목 한 쪽과 전체 개수. */
public record ReviewItemPage(List<ReviewItem> items, long totalElements) {

  public ReviewItemPage {
    items = List.copyOf(items);
  }
}
