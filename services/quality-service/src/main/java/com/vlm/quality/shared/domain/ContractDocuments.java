package com.vlm.quality.shared.domain;

/** 내가 쓰는 계약 문서(Golden Set 매니페스트 등)를 저장하기 전에 계약 JSON Schema로 확인한다. */
public interface ContractDocuments {

  /**
   * 문서가 계약에 맞는지 본다.
   *
   * @param schemaPath 계약 루트 아래 경로. 예: {@code quality/v1/golden-manifest.schema.json}
   * @param json 문서 JSON
   * @throws IllegalStateException 계약과 맞지 않을 때. 내가 만든 문서라 버그다
   */
  void requireValid(String schemaPath, String json);
}
