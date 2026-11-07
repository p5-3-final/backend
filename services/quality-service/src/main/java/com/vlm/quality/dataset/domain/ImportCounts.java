package com.vlm.quality.dataset.domain;

/**
 * 가져오기 개수(계약 {@code DatasetImport.counts}).
 *
 * @param labels 매니페스트의 라벨 수(검증 통과 뒤)
 * @param dev 개발 분할에 등록된 영상 수
 * @param eval 평가 분할에 등록된 영상 수
 */
public record ImportCounts(int videos, int registered, int failed, int labels, int dev, int eval) {

  public static final ImportCounts ZERO = new ImportCounts(0, 0, 0, 0, 0, 0);
}
