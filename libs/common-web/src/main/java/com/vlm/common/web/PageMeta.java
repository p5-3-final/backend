package com.vlm.common.web;

/**
 * 목록 응답의 페이지 정보(계약 _common.yaml {@code PageMeta}). 목록 API를 두는 서블릿 서비스가 함께 쓴다.
 *
 * @param page 0부터 세는 페이지 번호
 * @param size 페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages 전체 페이지 수
 */
public record PageMeta(int page, int size, long totalElements, int totalPages) {

  /** 전체 항목 수로 전체 페이지 수를 계산해 만든다. */
  public static PageMeta of(int page, int size, long totalElements) {
    return new PageMeta(
        page, size, totalElements, Math.toIntExact((totalElements + size - 1) / size));
  }
}
