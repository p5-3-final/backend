package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.vlm.pipeline.api.NormalizedEventParser;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NormalizedContractTest {
  final NormalizedEventParser parser = new NormalizedEventParser();

  ObjectNode ready() throws Exception {
    return (ObjectNode)
        new ObjectMapper()
            .readTree(
                Objects.requireNonNull(
                    getClass()
                        .getResourceAsStream("/contracts/events/video.normalized.v2.schema.json")))
            .get("examples")
            .get(0)
            .deepCopy();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "eventId",
        "sourceId",
        "ownerId",
        "normalizationId",
        "occurredAt",
        "eventType",
        "schemaVersion",
        "durationMs",
        "durationSec",
        "normalizedKey",
        "hlsPlaylistKey",
        "result",
        "fps",
        "width",
        "normalizationVersion"
      })
  @DisplayName("[SPEC-008 R-1] 계약의 필수 필드 누락을 거절한다")
  void required(String field) throws Exception {
    var j = ready();
    j.remove(field);
    assertThatThrownBy(() -> parser.parse(j.path("sourceId").asText(), j.toString()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "units",
        "fraction",
        "uuid",
        "time",
        "path",
        "version",
        "trailing",
        "duplicate",
        "key",
        "overflow"
      })
  @DisplayName("[SPEC-008 R-1 R-12] 경계 입력·중복 JSON 키·시간 불일치·경로 순회를 거절한다")
  void malformed(String kind) throws Exception {
    var j = ready();
    String key = j.get("sourceId").asText();
    switch (kind) {
      case "units" -> j.put("durationSec", 1);
      case "fraction" -> j.put("durationMs", new java.math.BigDecimal("125001.5"));
      case "uuid" -> j.put("eventId", "1-1-1-1-1");
      case "time" -> j.put("occurredAt", "yesterday");
      case "path" ->
          j.put(
              "normalizedKey",
              j.get("normalizedKey").asText().replace("normalized.mp4", "../normalized.mp4"));
      case "version" -> j.put("schemaVersion", 1);
      case "key" -> key = UUID.randomUUID().toString();
      case "overflow" -> j.put("durationMs", new java.math.BigInteger("9223372036854775808"));
      default -> {}
    }
    String payload = j.toString();
    if (kind.equals("trailing")) payload += " {}";
    if (kind.equals("duplicate"))
      payload = payload.substring(0, payload.length() - 1) + ",\"schemaVersion\":2}";
    String k = key, p = payload;
    assertThatThrownBy(() -> parser.parse(k, p)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("[SPEC-008 R-1] 호환 계약은 기존 v1과 새 v2 예제를 모두 검증한다")
  void compatibility() throws Exception {
    var root =
        new ObjectMapper()
            .readTree(
                Objects.requireNonNull(
                    getClass()
                        .getResourceAsStream("/contracts/events/video.normalized.schema.json")));
    var schema =
        JsonSchemaFactory.getInstance(
                SpecVersion.VersionFlag.V202012,
                b ->
                    b.schemaMappers(
                        m ->
                            m.mapPrefix(
                                "https://vlm.example/contracts/events/",
                                "classpath:contracts/events/")))
            .getSchema(
                SchemaLocation.of(
                    "https://vlm.example/contracts/events/video.normalized.schema.json"));
    assertThat(root.get("examples")).hasSize(4);
    for (JsonNode example : root.get("examples")) assertThat(schema.validate(example)).isEmpty();
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "599999,60",
    "600000,120",
    "600001,120",
    "7199999,120",
    "7200000,120",
    "7200001,180"
  })
  @DisplayName("[SPEC-008 R-2 R-12] 정수 밀리초 변환이 운영 청크 경계를 보존한다")
  void boundaries(long ms, int seconds) {
    var event =
        new com.vlm.pipeline.domain.NormalizedVideo(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "v1",
            true,
            ms,
            "{}",
            "{}");
    var input =
        new com.vlm.pipeline.domain.AnalysisSettings("v1", "production", "infer-v1")
            .plan(UUID.randomUUID(), event);
    assertThat(input.chunkLength()).isEqualByComparingTo(String.valueOf(seconds));
    assertThat(input.duration().movePointRight(3).longValueExact()).isEqualTo(ms);
  }
}
