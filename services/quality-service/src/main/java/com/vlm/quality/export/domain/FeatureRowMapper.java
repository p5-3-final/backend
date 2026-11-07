package com.vlm.quality.export.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** 결과 파일(aggregates/v1/source-result)의 위험 상태를 특징 줄(quality/v1/feature-row)로 바꾼다(SPEC-030 R-14). */
public final class FeatureRowMapper {

  private static final int SCHEMA_VERSION = 1;
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private FeatureRowMapper() {}

  /**
   * 위험 상태 하나가 한 줄이다.
   *
   * @param split Golden Set으로 고른 영상이면 그 분할(dev·eval), 기간으로 골랐으면 null
   */
  public static List<ObjectNode> rows(JsonNode sourceResult, @Nullable String split) {
    Map<String, JsonNode> forecasts = forecastsByRisk(sourceResult);
    List<ObjectNode> rows = new ArrayList<>();
    for (JsonNode risk : sourceResult.path("risks")) {
      rows.add(row(sourceResult, risk, forecasts, split));
    }
    return List.copyOf(rows);
  }

  private static ObjectNode row(
      JsonNode result, JsonNode risk, Map<String, JsonNode> forecasts, @Nullable String split) {
    JsonNode exposure = risk.path("exposure");
    String riskId = risk.path("riskId").asText();
    ObjectNode row = NODES.objectNode();
    row.put("schemaVersion", SCHEMA_VERSION);
    row.set("sourceId", result.path("sourceId"));
    row.set("jobId", result.path("jobId"));
    row.put("riskId", riskId);
    row.set("riskType", risk.path("riskType"));
    row.set("zoneId", risk.path("zoneId"));
    row.set("status", risk.path("status"));
    row.set("carriedOver", risk.path("carriedOver"));
    row.set("firstSeenMs", risk.path("firstSeenMs"));
    // 위험이 놓인 영상 길이다. 학습 쪽은 activeSeconds와 함께 영상 끝까지 남은 시간을 계산한다.
    row.set("durationMs", result.path("durationMs"));
    row.set("activeSeconds", exposure.path("activeSeconds"));
    row.set("exposureCount", exposure.path("exposureCount"));
    row.set("exposureSeconds", exposure.path("exposureSeconds"));
    row.put("relatedEventCount", risk.path("relatedEventIds").size());
    row.set("forecast", forecasts.getOrDefault(riskId, NODES.nullNode()));
    row.put("split", split);
    return row;
  }

  // forecasts는 SPEC-028 이전 결과 파일에 없을 수 있다. status가 generated가 아니면 forecast가 null이다.
  private static Map<String, JsonNode> forecastsByRisk(JsonNode result) {
    Map<String, JsonNode> byRisk = new HashMap<>();
    for (JsonNode entry : result.path("forecasts")) {
      JsonNode forecast = entry.path("forecast");
      if (forecast.isObject()) {
        byRisk.put(entry.path("riskId").asText(), forecast);
      }
    }
    return byRisk;
  }
}
