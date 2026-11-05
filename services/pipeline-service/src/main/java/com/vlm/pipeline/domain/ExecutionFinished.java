package com.vlm.pipeline.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 실제 종료를 나타내는 입력. 모델 결과 의미는 해석하지 않는다. */
public record ExecutionFinished(
    UUID eventId,
    UUID executionId,
    UUID jobId,
    UUID chunkId,
    UUID normalizationId,
    UUID sourceId,
    UUID ownerId,
    String outcome,
    @Nullable String resultKey,
    @Nullable String errorCode,
    String payload) {
  public String semantic() {
    return "{\"outcome\":\""
        + outcome
        + "\",\"resultKey\":"
        + quoted(resultKey)
        + ",\"errorCode\":"
        + quoted(errorCode)
        + "}";
  }

  private static String quoted(@Nullable String value) {
    return value == null ? "null" : "\"" + value + "\"";
  }

  public static ExecutionFinished from(JsonNode n) {
    return new ExecutionFinished(
        id(n, "eventId"),
        id(n, "executionId"),
        id(n, "jobId"),
        id(n, "chunkId"),
        id(n, "normalizationId"),
        id(n, "sourceId"),
        id(n, "ownerId"),
        n.get("outcome").asText(),
        n.get("resultKey").isNull() ? null : n.get("resultKey").asText(),
        n.get("errorCode").isNull() ? null : n.get("errorCode").asText(),
        n.toString());
  }

  private static UUID id(JsonNode n, String field) {
    String value = n.get(field).asText();
    UUID id = UUID.fromString(value);
    if (!id.toString().equals(value))
      throw new IllegalArgumentException("Non canonical result identifier");
    return id;
  }
}
