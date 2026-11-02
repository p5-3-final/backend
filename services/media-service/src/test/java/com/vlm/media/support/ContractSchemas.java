package com.vlm.media.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.file.Path;
import java.util.Set;

/** docs 레포의 이벤트 계약(JSON Schema)으로 payload를 검증한다. 원본 경로는 빌드가 넘긴다(vlm.contracts.dir). */
public final class ContractSchemas {

  // 이벤트 스키마의 $id 접두사. $ref("_envelope.schema.json")를 파일로 찾도록 로컬 경로에 연결한다.
  private static final String ID_PREFIX = "https://vlm.example/contracts/events/";

  private ContractSchemas() {}

  public static Set<ValidationMessage> validateEvent(String schemaFile, JsonNode payload) {
    Path events = Path.of(System.getProperty("vlm.contracts.dir"), "events");
    JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder ->
                builder.schemaMappers(
                    mappers -> mappers.mapPrefix(ID_PREFIX, events.toUri().toString())));
    JsonSchema schema = factory.getSchema(SchemaLocation.of(ID_PREFIX + schemaFile));
    return schema.validate(payload);
  }
}
