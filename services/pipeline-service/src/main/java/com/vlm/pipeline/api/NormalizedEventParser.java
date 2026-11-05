package com.vlm.pipeline.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.vlm.pipeline.domain.NormalizedVideo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 배포 JAR에 포함한 docs v2 계약으로 수신을 검증한다. 원격 스키마를 조회하지 않는다. */
@Component
public class NormalizedEventParser {
  private final ObjectMapper mapper =
      JsonMapper.builder(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(
              DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS,
              DeserializationFeature.USE_BIG_INTEGER_FOR_INTS,
              DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private final JsonSchema schema =
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
                  "https://vlm.example/contracts/events/video.normalized.v2.schema.json"));

  public NormalizedVideo parse(String key, String payload) {
    try {
      JsonNode node = Objects.requireNonNull(mapper.readTree(payload));
      if (!schema.validate(node).isEmpty())
        throw new IllegalArgumentException("Invalid normalized v2 event");
      UUID eventId = uuid(node, "eventId"),
          sourceId = uuid(node, "sourceId"),
          ownerId = uuid(node, "ownerId"),
          normalizationId = uuid(node, "normalizationId");
      if (!sourceId.equals(UUID.fromString(key)))
        throw new IllegalArgumentException("Kafka key does not match sourceId");
      Instant.parse(text(node, "occurredAt"));
      boolean ready = text(node, "result").equals("READY");
      long ms = 0;
      if (ready) {
        JsonNode duration = Objects.requireNonNull(node.get("durationMs"));
        if (!duration.canConvertToLong())
          throw new IllegalArgumentException("durationMs exceeds integer range");
        ms = duration.longValue();
        if (Objects.requireNonNull(node.get("durationSec"))
                .decimalValue()
                .compareTo(BigDecimal.valueOf(ms, 3))
            != 0) throw new IllegalArgumentException("Duration units do not match");
        String prefix = "sources/" + sourceId + "/normalizations/" + normalizationId + "/";
        validateKey(text(node, "normalizedKey"), prefix, ".mp4");
        validateKey(text(node, "hlsPlaylistKey"), prefix, ".m3u8");
      }
      ObjectNode metadata = ((ObjectNode) node).deepCopy();
      metadata.remove(
          java.util.List.of("eventId", "occurredAt", "eventType", "schemaVersion", "producedAt"));
      return new NormalizedVideo(
          eventId,
          normalizationId,
          sourceId,
          ownerId,
          text(node, "normalizationVersion"),
          ready,
          ms,
          node.toString(),
          metadata.toString());
    } catch (java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid occurredAt timestamp", e);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Malformed normalized JSON", e);
    }
  }

  private static String text(JsonNode node, String field) {
    return Objects.requireNonNull(node.get(field)).asText();
  }

  private static UUID uuid(JsonNode node, String field) {
    String value = text(node, field);
    UUID id = UUID.fromString(value);
    if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID");
    return id;
  }

  private static void validateKey(String value, String prefix, String suffix) {
    if (!value.startsWith(prefix)
        || !value.endsWith(suffix)
        || value.contains("?")
        || value.contains("#")
        || java.util.Arrays.asList(value.split("/", -1)).contains(".."))
      throw new IllegalArgumentException("Invalid immutable object key");
  }
}
