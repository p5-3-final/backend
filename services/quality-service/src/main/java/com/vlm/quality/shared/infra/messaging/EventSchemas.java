package com.vlm.quality.shared.infra.messaging;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 받은 메시지를 배포 JAR에 넣은 계약 JSON Schema(빌드가 docs {@code service-contracts}에서 복사)로 검증한다.
 *
 * <p>원격 스키마를 조회하지 않는다. 계약 {@code $id}를 클래스패스 {@code contracts/}에 연결해 상대 {@code $ref}도 JAR 안에서 찾는다.
 */
@Component
public class EventSchemas {

  private static final String ID_PREFIX = "https://vlm.example/contracts/";

  // 중복 키는 계약 위반으로 본다. 큰 정수·소수는 정밀도를 잃지 않게 읽는다.
  private final ObjectMapper strict =
      JsonMapper.builder(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .build();
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(
          SpecVersion.VersionFlag.V202012,
          builder ->
              builder.schemaMappers(
                  mappers -> mappers.mapPrefix(ID_PREFIX, "classpath:contracts/")));
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

  /**
   * 메시지를 읽고 계약으로 검증한다.
   *
   * @param schemaPath {@code contracts/} 아래 경로. 예: {@code events/event.analyzed.schema.json}
   * @throws InvalidEventException JSON이 아니거나 계약과 맞지 않을 때
   */
  public JsonNode readValid(String schemaPath, String payload) {
    JsonNode node = read(payload);
    validate(schemaPath, node);
    return node;
  }

  /** 이미 읽은 문서를 계약으로 검증한다. 내가 쓰는 문서(매니페스트)를 저장 전에 확인할 때도 쓴다. */
  public void validate(String schemaPath, JsonNode node) {
    Set<ValidationMessage> errors = schema(schemaPath).validate(node);
    if (!errors.isEmpty()) {
      throw new InvalidEventException(
          "does not match " + schemaPath + ": " + errors.iterator().next().getMessage());
    }
  }

  private JsonNode read(String payload) {
    try {
      JsonNode node = strict.readTree(payload);
      if (node == null || !node.isObject()) {
        throw new InvalidEventException("message is not a JSON object");
      }
      return node;
    } catch (JsonProcessingException e) {
      throw new InvalidEventException("message is not JSON", e);
    }
  }

  private JsonSchema schema(String schemaPath) {
    return schemas.computeIfAbsent(
        schemaPath,
        path ->
            factory.getSchema(
                SchemaLocation.of(ID_PREFIX + path),
                // format(uuid, date-time)도 계약의 일부라 검사한다.
                SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build()));
  }
}
