package com.vlm.quality.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.nio.file.Path;
import java.util.Set;

/** docs 레포 원본 계약(JSON Schema)으로 문서를 검증한다. 원본 경로는 빌드가 넘긴다(vlm.contracts.dir). */
public final class ContractSchemas {

  private static final String ID_PREFIX = "https://vlm.example/contracts/";

  private ContractSchemas() {}

  public static Set<ValidationMessage> validate(String path, JsonNode document) {
    Path root = Path.of(System.getProperty("vlm.contracts.dir"));
    JsonSchemaFactory factory =
        JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder ->
                builder.schemaMappers(
                    mappers -> mappers.mapPrefix(ID_PREFIX, root.toUri().toString())));
    return factory
        .getSchema(
            SchemaLocation.of(ID_PREFIX + path),
            SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build())
        .validate(document);
  }
}
