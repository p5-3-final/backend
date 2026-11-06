package com.vlm.quality.shared.api;

import vlm.contracts.quality.model.ListReviewItems200ResponsePage;

/** 계약 PageMeta 응답. 생성기가 목록마다 같은 모양을 검토 큐 목록 이름 하나로 만들어 모든 목록이 함께 쓴다. */
public final class PageMetas {

  private PageMetas() {}

  public static ListReviewItems200ResponsePage of(int page, int size, long total) {
    return new ListReviewItems200ResponsePage(
        page, size, Math.toIntExact(total), Math.toIntExact((total + size - 1) / size));
  }
}
