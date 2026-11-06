package com.vlm.media.shared.domain;

import java.util.List;

/** 세션 목록 한 페이지. {@code page}는 0부터 센다. */
public record WorkSessionPage(List<WorkSession> items, int page, int size, long totalElements) {

  public int totalPages() {
    return Math.toIntExact((totalElements + size - 1) / size);
  }
}
