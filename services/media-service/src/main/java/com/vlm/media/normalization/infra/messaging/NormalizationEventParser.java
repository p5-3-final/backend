package com.vlm.media.normalization.infra.messaging;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.normalization.application.NormalizationOutcome;
import com.vlm.media.shared.domain.NormalizationResult;
import com.vlm.media.shared.domain.RejectReason;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * {@code video.normalizing}·{@code video.normalized} 메시지를 계약(docs {@code contracts/events})대로 읽는다.
 *
 * <p>계약을 어긴 메시지는 다시 받아도 같으므로 {@link InvalidEventException}으로 알린다.
 */
@Component
@RequiredArgsConstructor
public class NormalizationEventParser {

  private static final String NORMALIZING = "video.normalizing";
  private static final String NORMALIZED = "video.normalized";
  // normalize는 ADR-0009의 v2만 발행한다. v1은 정규화 ID가 없어 결과를 구분할 수 없다.
  private static final int NORMALIZED_VERSION = 2;
  private static final Pattern VERSION_PATTERN =
      Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$");
  private static final List<String> READY_FIELDS =
      List.of(
          "normalizedKey",
          "hlsPlaylistKey",
          "durationMs",
          "durationSec",
          "fps",
          "width",
          "height",
          "transcoded");

  private final ObjectMapper json;

  /** 시작 신호에서 영상 ID를 읽는다(SPEC-007 R-15). */
  public UUID parseNormalizing(@Nullable String key, String value) {
    JsonNode event = read(value);
    requireEnvelope(event, NORMALIZING, 1);
    return sourceId(event, key);
  }

  /** 정규화 결과를 읽는다(SPEC-007 R-16, R-17). */
  public NormalizationOutcome parseNormalized(@Nullable String key, String value) {
    JsonNode event = read(value);
    requireEnvelope(event, NORMALIZED, NORMALIZED_VERSION);
    UUID sourceId = sourceId(event, key);
    UUID normalizationId = uuid(event, "normalizationId");
    String version = text(event, "normalizationVersion");
    if (!VERSION_PATTERN.matcher(version).matches()) {
      throw new InvalidEventException("normalizationVersion has invalid format");
    }
    return switch (text(event, "result")) {
      case "READY" -> ready(event, sourceId, normalizationId, version);
      case "REJECTED" -> rejected(event, sourceId, normalizationId, version);
      default -> throw new InvalidEventException("unknown result");
    };
  }

  private NormalizationOutcome ready(
      JsonNode event, UUID sourceId, UUID normalizationId, String version) {
    if (!isNull(event, "rejectReason")) {
      throw new InvalidEventException("READY must not have rejectReason");
    }
    for (String field : READY_FIELDS) {
      if (isNull(event, field)) {
        throw new InvalidEventException("READY requires " + field);
      }
    }
    long durationMs = positiveLong(event, "durationMs");
    // 계약: durationSec는 durationMs/1000과 같아야 한다(ADR-0009). 소수 오차 없이 비교한다.
    if (event.get("durationSec").decimalValue().compareTo(BigDecimal.valueOf(durationMs, 3)) != 0) {
      throw new InvalidEventException("durationSec does not match durationMs");
    }
    String folder = "sources/" + sourceId + "/";
    var result =
        new NormalizationResult(
            normalizationId,
            version,
            storageKey(event, "normalizedKey", folder),
            storageKey(event, "hlsPlaylistKey", folder),
            durationMs);
    return new NormalizationOutcome.Ready(sourceId, result);
  }

  private NormalizationOutcome rejected(
      JsonNode event, UUID sourceId, UUID normalizationId, String version) {
    for (String field : READY_FIELDS) {
      if (!isNull(event, field)) {
        throw new InvalidEventException("REJECTED must not have " + field);
      }
    }
    RejectReason reason;
    try {
      reason = RejectReason.valueOf(text(event, "rejectReason"));
    } catch (IllegalArgumentException e) {
      throw new InvalidEventException("unknown rejectReason", e);
    }
    return new NormalizationOutcome.Rejected(sourceId, normalizationId, version, reason);
  }

  private JsonNode read(String value) {
    try {
      JsonNode node = json.readTree(value);
      if (node == null || !node.isObject()) {
        throw new InvalidEventException("message is not a JSON object");
      }
      return node;
    } catch (JacksonException e) {
      throw new InvalidEventException("message is not JSON", e);
    }
  }

  private static void requireEnvelope(JsonNode event, String eventType, int schemaVersion) {
    if (!eventType.equals(text(event, "eventType"))) {
      throw new InvalidEventException("unexpected eventType");
    }
    JsonNode version = event.get("schemaVersion");
    if (version == null || !version.isIntegralNumber() || version.asInt() != schemaVersion) {
      throw new InvalidEventException("unsupported schemaVersion");
    }
    uuid(event, "eventId");
    uuid(event, "ownerId");
    text(event, "occurredAt");
  }

  // 모든 토픽의 키는 sourceId다. 키가 다르면 순서 보장이 깨진 메시지다.
  private static UUID sourceId(JsonNode event, @Nullable String key) {
    UUID sourceId = uuid(event, "sourceId");
    if (!sourceId.toString().equals(key)) {
      throw new InvalidEventException("message key does not match sourceId");
    }
    return sourceId;
  }

  // 다른 영상의 객체를 가리키는 결과를 받아들이면 삭제·재생이 엉뚱한 객체를 건드린다.
  private static String storageKey(JsonNode event, String field, String folder) {
    String key = text(event, field);
    if (!key.startsWith(folder) || key.contains("..")) {
      throw new InvalidEventException(field + " is outside the source folder");
    }
    return key;
  }

  private static boolean isNull(JsonNode event, String field) {
    JsonNode node = event.get(field);
    return node == null || node.isNull();
  }

  private static String text(JsonNode event, String field) {
    JsonNode node = event.get(field);
    if (node == null || !node.isTextual()) {
      throw new InvalidEventException(field + " must be a string");
    }
    return node.asText();
  }

  private static UUID uuid(JsonNode event, String field) {
    String value = text(event, field);
    try {
      UUID parsed = UUID.fromString(value);
      // UUID.fromString은 자릿수가 모자란 값도 받아들이므로 정규 형식과 다시 비교한다.
      if (!parsed.toString().equalsIgnoreCase(value)) {
        throw new InvalidEventException(field + " must be a UUID");
      }
      return parsed;
    } catch (IllegalArgumentException e) {
      throw new InvalidEventException(field + " must be a UUID", e);
    }
  }

  private static long positiveLong(JsonNode event, String field) {
    JsonNode node = event.get(field);
    if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() < 1) {
      throw new InvalidEventException(field + " must be a positive integer");
    }
    return node.asLong();
  }
}
