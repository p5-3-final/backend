package com.vlm.quality.golden.application;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 가져오기가 Golden Set에 넣을 등록된 영상과 라벨(SPEC-030 R-8). dataset 기능이 golden 기능에 넘기는 값이다.
 *
 * @param datasetName Golden Set 이름 접두사. {@code <이름>-dev}, {@code <이름>-eval}에 들어간다
 * @param operator 확정 버전의 frozenBy
 */
public record DatasetBatch(
    String datasetName, DatasetOrigin origin, UUID importId, UUID operator, List<Entry> entries) {

  public DatasetBatch {
    entries = List.copyOf(entries);
  }

  /** 출처·사용 조건(계약 golden-manifest {@code datasetOrigin}). */
  public record DatasetOrigin(String name, String license, @Nullable String url) {}

  /**
   * 등록된 영상 하나.
   *
   * @param split dev·eval
   * @param labelsJson 매니페스트 라벨 배열 그대로(JSON)
   */
  public record Entry(String externalId, UUID sourceId, String split, String labelsJson) {}
}
