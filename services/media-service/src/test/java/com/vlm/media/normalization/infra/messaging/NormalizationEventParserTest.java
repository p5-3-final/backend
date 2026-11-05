package com.vlm.media.normalization.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.media.normalization.application.NormalizationOutcome;
import com.vlm.media.shared.domain.RejectReason;
import com.vlm.media.support.ContractSchemas;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NormalizationEventParserTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String V2 = "video.normalized.v2.schema.json";

  private final NormalizationEventParser parser = new NormalizationEventParser(JSON);

  @Test
  @DisplayName("[SPEC-007 R-16] 계약의 READY 예시를 결과로 읽는다")
  void readsReadyExample() throws IOException {
    ObjectNode ready = example(V2, 0);
    String source = ready.get("sourceId").asText();

    NormalizationOutcome outcome = parser.parseNormalized(source, ready.toString());

    assertThat(outcome).isInstanceOf(NormalizationOutcome.Ready.class);
    NormalizationOutcome.Ready r = (NormalizationOutcome.Ready) outcome;
    assertThat(r.sourceId()).hasToString(source);
    assertThat(r.result().normalizationId()).hasToString(ready.get("normalizationId").asText());
    assertThat(r.result().version()).isEqualTo("normalize-v1");
    assertThat(r.result().normalizedKey()).isEqualTo(ready.get("normalizedKey").asText());
    assertThat(r.result().hlsPlaylistKey()).isEqualTo(ready.get("hlsPlaylistKey").asText());
    assertThat(r.result().durationMs()).isEqualTo(125_001L);
  }

  @Test
  @DisplayName("[SPEC-007 R-17] 계약의 REJECTED 예시를 거절 결과로 읽는다")
  void readsRejectedExample() throws IOException {
    ObjectNode rejected = example(V2, 1);

    NormalizationOutcome outcome =
        parser.parseNormalized(rejected.get("sourceId").asText(), rejected.toString());

    assertThat(outcome)
        .isInstanceOfSatisfying(
            NormalizationOutcome.Rejected.class,
            r -> {
              assertThat(r.reason()).isEqualTo(RejectReason.NO_VIDEO_STREAM);
              assertThat(r.normalizationId()).hasToString(rejected.get("normalizationId").asText());
            });
  }

  @Test
  @DisplayName("[SPEC-007 R-15] 계약의 시작 신호 예시에서 영상 ID를 읽는다")
  void readsNormalizingExample() throws IOException {
    ObjectNode started = example("video.normalizing.schema.json", 0);
    String source = started.get("sourceId").asText();

    assertThat(parser.parseNormalizing(source, started.toString())).hasToString(source);
  }

  static Stream<Arguments> contractViolations() {
    return Stream.of(
        violation("READY인데 거절 사유가 있음", n -> n.put("rejectReason", "TOO_LONG")),
        violation("READY인데 길이가 없음", n -> n.putNull("durationMs")),
        violation("READY인데 fps가 없음", n -> n.putNull("fps")),
        violation("모르는 거절 사유", n -> n.put("result", "REJECTED").put("rejectReason", "NOPE")),
        violation("결과 값이 다름", n -> n.put("result", "DONE")),
        violation("이벤트 유형이 다름", n -> n.put("eventType", "video.uploaded")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("contractViolations")
  @DisplayName("[SPEC-007] 계약과 맞지 않는 결과 메시지는 거절한다")
  void rejectsContractViolations(String name, Consumer<ObjectNode> breakIt) throws IOException {
    ObjectNode message = example(V2, 0);
    breakIt.accept(message);

    // 해석기가 계약보다 느슨하거나 엄격하지 않은지 원본 스키마로도 확인한다.
    assertThat(ContractSchemas.validateEvent(V2, message)).isNotEmpty();
    assertThatThrownBy(
            () -> parser.parseNormalized(message.get("sourceId").asText(), message.toString()))
        .isInstanceOf(InvalidEventException.class);
  }

  static Stream<Arguments> rulesBeyondSchema() {
    return Stream.of(
        violation("길이 초가 밀리초와 다름", n -> n.put("durationSec", 125.0)),
        violation("normalizationId가 UUID가 아님", n -> n.put("normalizationId", "abc")),
        // UUID.fromString은 자릿수가 모자란 값도 받아들인다.
        violation("normalizationId가 정규 UUID 형식이 아님", n -> n.put("normalizationId", "1-1-1-1-1")));
  }

  // JSON Schema로 표현하지 않았거나(길이 일치) 검증기가 강제하지 않는(format) 계약 규칙이다.
  @ParameterizedTest(name = "{0}")
  @MethodSource("rulesBeyondSchema")
  @DisplayName("[SPEC-007] 스키마 밖의 계약 규칙을 어긴 결과 메시지는 거절한다")
  void rejectsRulesBeyondSchema(String name, Consumer<ObjectNode> breakIt) throws IOException {
    ObjectNode message = example(V2, 0);
    breakIt.accept(message);

    assertThatThrownBy(
            () -> parser.parseNormalized(message.get("sourceId").asText(), message.toString()))
        .isInstanceOf(InvalidEventException.class);
  }

  @Test
  @DisplayName("[SPEC-007] v1 결과는 처리하지 않는다(normalize는 v2만 발행한다)")
  void rejectsVersionOne() throws IOException {
    ObjectNode v1 = example("video.normalized.schema.json", 0);

    assertThatThrownBy(() -> parser.parseNormalized(v1.get("sourceId").asText(), v1.toString()))
        .isInstanceOf(InvalidEventException.class)
        .hasMessageContaining("schemaVersion");
  }

  @Test
  @DisplayName("[SPEC-007] 메시지 키가 sourceId와 다르면 거절한다")
  void rejectsKeyMismatch() throws IOException {
    ObjectNode ready = example(V2, 0);

    assertThatThrownBy(() -> parser.parseNormalized(UUID.randomUUID().toString(), ready.toString()))
        .isInstanceOf(InvalidEventException.class);
  }

  @Test
  @DisplayName("[SPEC-007] 저장 경로가 그 영상 폴더 밖이면 거절한다")
  void rejectsForeignStorageKey() throws IOException {
    ObjectNode ready = example(V2, 0);
    ready.put("normalizedKey", "sources/" + UUID.randomUUID() + "/normalized.mp4");

    assertThatThrownBy(
            () -> parser.parseNormalized(ready.get("sourceId").asText(), ready.toString()))
        .isInstanceOf(InvalidEventException.class);
  }

  @Test
  @DisplayName("[SPEC-007] JSON이 아니면 거절한다")
  void rejectsMalformedJson() {
    assertThatThrownBy(() -> parser.parseNormalized(null, "{not json"))
        .isInstanceOf(InvalidEventException.class);
  }

  private static Arguments violation(String name, Consumer<ObjectNode> breakIt) {
    return Arguments.of(name, breakIt);
  }

  private static ObjectNode example(String schemaFile, int index) throws IOException {
    Path file = Path.of(System.getProperty("vlm.contracts.dir"), "events", schemaFile);
    JsonNode schema = JSON.readTree(Files.readString(file));
    JsonNode example = schema.get("examples").get(index);
    return (ObjectNode) example.deepCopy();
  }
}
