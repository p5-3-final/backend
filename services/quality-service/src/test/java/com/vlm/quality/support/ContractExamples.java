package com.vlm.quality.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** 계약 JSON Schema의 {@code examples}를 테스트 메시지로 쓴다. 원본 경로는 빌드가 넘긴다(vlm.contracts.dir). */
public final class ContractExamples {

  private static final ObjectMapper JSON = new ObjectMapper();

  private ContractExamples() {}

  /** {@code contracts/<path>}의 n번째 예시를 고칠 수 있는 사본으로 돌려준다. */
  public static ObjectNode example(String path, int index) {
    try {
      JsonNode schema =
          JSON.readTree(Path.of(System.getProperty("vlm.contracts.dir"), path).toFile());
      return (ObjectNode) schema.get("examples").get(index).deepCopy();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
