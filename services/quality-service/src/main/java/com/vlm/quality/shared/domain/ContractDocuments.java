package com.vlm.quality.shared.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.jspecify.annotations.Nullable;

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

  /**
   * 밖에서 받은 문서(가져오기 매니페스트, 결과 파일)를 읽고 계약으로 확인한다.
   *
   * @param maxViolations 남길 위반 메시지 수 상한
   * @return 위반이 없으면 {@code violations}가 비어 있다. JSON이 아니면 {@code document}가 null이다
   */
  ParsedDocument parse(String schemaPath, byte[] json, int maxViolations);

  /** 읽은 문서와 계약 위반(위치: 메시지). */
  record ParsedDocument(@Nullable JsonNode document, List<String> violations) {

    public ParsedDocument {
      violations = List.copyOf(violations);
    }

    public boolean valid() {
      return document != null && violations.isEmpty();
    }
  }
}
