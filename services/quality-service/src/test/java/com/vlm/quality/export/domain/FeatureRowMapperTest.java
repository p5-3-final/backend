package com.vlm.quality.export.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.support.ContractExamples;
import com.vlm.quality.support.ContractSchemas;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FeatureRowMapperTest {

  private static final String SOURCE_RESULT = "aggregates/v1/source-result.schema.json";
  private static final String FEATURE_ROW = "quality/v1/feature-row.schema.json";
  private static final String FORECAST = "events/_risk-forecast.schema.json";

  @Test
  @DisplayName("[SPEC-030 R-14] 계약 결과 파일 예시의 위험 상태가 계약 특징 줄 예시와 같은 줄이 된다")
  void mapsContractExample() {
    ObjectNode result = ContractExamples.example(SOURCE_RESULT, 0);

    List<ObjectNode> rows = FeatureRowMapper.rows(result, null);

    assertThat(rows).containsExactly(ContractExamples.example(FEATURE_ROW, 0));
    assertThat(ContractSchemas.validate(FEATURE_ROW, rows.get(0))).isEmpty();
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 위험마다 한 줄이고 같은 riskId의 예측과 Golden Set 분할을 붙인다")
  void attachesForecastAndSplit() {
    ObjectNode result = ContractExamples.example(SOURCE_RESULT, 0);
    ArrayNode risks = (ArrayNode) result.get("risks");
    ObjectNode second = risks.get(0).deepCopy();
    second.put("riskId", "0192f500-0000-5000-8000-000000000005");
    second.put("status", "resolved");
    second.putArray("relatedEventIds");
    risks.add(second);
    JsonNode forecast = ContractExamples.example(FORECAST, 0);
    ObjectNode entry = result.putArray("forecasts").addObject();
    entry.put("riskId", "0192f500-0000-5000-8000-000000000005");
    entry.put("status", "generated");
    entry.set("forecast", forecast);
    entry.put("callId", "0192f600-0000-5000-8000-000000000001");
    assertThat(ContractSchemas.validate(SOURCE_RESULT, result)).isEmpty();

    List<ObjectNode> rows = FeatureRowMapper.rows(result, "eval");

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("forecast").isNull()).isTrue();
    assertThat(rows.get(1).get("forecast")).isEqualTo(forecast);
    assertThat(rows.get(1).get("relatedEventCount").asInt()).isZero();
    assertThat(rows).allSatisfy(row -> assertThat(row.get("split").asText()).isEqualTo("eval"));
    assertThat(rows)
        .allSatisfy(row -> assertThat(ContractSchemas.validate(FEATURE_ROW, row)).isEmpty());
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 위험 상태가 없는 결과 파일은 줄이 없다")
  void noRisksNoRows() {
    ObjectNode result = ContractExamples.example(SOURCE_RESULT, 0);
    result.putArray("risks");

    assertThat(FeatureRowMapper.rows(result, null)).isEmpty();
  }
}
