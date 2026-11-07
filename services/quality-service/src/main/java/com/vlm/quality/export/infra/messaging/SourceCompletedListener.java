package com.vlm.quality.export.infra.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.export.application.SourceCompleted;
import com.vlm.quality.export.application.SourceResultService;
import com.vlm.quality.shared.infra.messaging.EventFields;
import com.vlm.quality.shared.infra.messaging.EventSchemas;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 영상 결과 파일 경로를 받는다(SPEC-030 R-13). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.quality.consumers-enabled",
    havingValue = "true",
    matchIfMissing = true)
class SourceCompletedListener {

  private static final String SCHEMA = "events/source.completed.schema.json";

  private final EventSchemas schemas;
  private final SourceResultService results;

  @KafkaListener(
      id = "quality-export",
      topics = "source.completed",
      groupId = "quality-service",
      containerFactory = "qualityKafkaListenerContainerFactory")
  void receive(ConsumerRecord<String, String> record) {
    results.record(parse(schemas.readValid(SCHEMA, record.value()), record.key()));
  }

  private static SourceCompleted parse(JsonNode event, @Nullable String key) {
    return new SourceCompleted(
        EventFields.sourceIdMatchingKey(event, key),
        EventFields.uuid(event, "jobId"),
        // 계약: revision이 없으면 1이다.
        event.path("revision").asInt(1),
        EventFields.text(event, "resultKey"));
  }
}
