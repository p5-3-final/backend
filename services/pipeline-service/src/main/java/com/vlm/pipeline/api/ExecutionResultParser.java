package com.vlm.pipeline.api;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.networknt.schema.*;
import com.vlm.pipeline.domain.ExecutionFinished;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class ExecutionResultParser {
  private final ObjectMapper mapper =
      JsonMapper.builder(
              JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
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
                  "https://vlm.example/contracts/events/chunk.execution.finished.schema.json"));

  public ExecutionFinished parse(String key, String payload) {
    try {
      var n = Objects.requireNonNull(mapper.readTree(payload));
      if (!schema.validate(n).isEmpty())
        throw new IllegalArgumentException("Invalid execution result schema");
      Instant.parse(n.get("occurredAt").asText());
      if (n.has("producedAt")) Instant.parse(n.get("producedAt").asText());
      var event = ExecutionFinished.from(n);
      if (!event.sourceId().toString().equals(key)
          || !n.get("requestId").asText().equals(event.executionId().toString()))
        throw new IllegalArgumentException("Result key or request identifier mismatch");
      if (event.resultKey() != null) {
        String expected =
            "sources/"
                + event.sourceId()
                + "/jobs/"
                + event.jobId()
                + "/chunks/"
                + event.chunkId()
                + "/executions/"
                + event.executionId()
                + "/result.json";
        if (!expected.equals(event.resultKey()))
          throw new IllegalArgumentException("Result object reference mismatch");
      }
      return event;
    } catch (com.fasterxml.jackson.core.JsonProcessingException | java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid execution result JSON", e);
    }
  }
}
