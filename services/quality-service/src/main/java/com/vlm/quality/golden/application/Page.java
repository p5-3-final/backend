package com.vlm.quality.golden.application;

import java.util.List;

/** 목록 한 쪽과 전체 개수. */
public record Page<T>(List<T> items, long totalElements) {

  public Page {
    items = List.copyOf(items);
  }
}
