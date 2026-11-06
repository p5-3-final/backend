package com.vlm.quality.stage.infra.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.shared.infra.messaging.EventFields;
import com.vlm.quality.shared.infra.messaging.EventSchemas;
import com.vlm.quality.stage.application.StageTimingBatch;
import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.stage.domain.StageTiming;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 모든 소비자가 보내는 처리 단계 측정 묶음을 받는다(SPEC-029 R-21, SPEC-013). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.quality.consumers-enabled",
    havingValue = "true",
    matchIfMissing = true)
class StageTimingListener {

  private static final String SCHEMA = "events/stage.timing.schema.json";

  private final EventSchemas schemas;
  private final StageTimingIngestService ingest;

  @KafkaListener(
      id = "quality-stage",
      topics = "stage.timing",
      groupId = "quality-service",
      containerFactory = "qualityKafkaListenerContainerFactory")
  void receive(ConsumerRecord<String, String> record) {
    JsonNode batch = schemas.readValid(SCHEMA, record.value());
    String service = EventFields.text(batch, "service");
    List<StageTiming> timings = new ArrayList<>();
    batch.get("timings").forEach(timing -> timings.add(timing(timing, service)));
    ingest.ingest(new StageTimingBatch(timings));
  }

  private static StageTiming timing(JsonNode timing, String service) {
    JsonNode subject = timing.get("subject");
    String sourceId = EventFields.nullableText(subject, "sourceId");
    Map<String, String> versions = new LinkedHashMap<>();
    JsonNode versionNode = timing.get("versions");
    if (versionNode != null) {
      versionNode.properties().forEach(e -> versions.put(e.getKey(), e.getValue().asText()));
    }
    return new StageTiming(
        EventFields.uuid(timing, "timingId"),
        EventFields.text(timing, "stageId"),
        EventFields.text(timing, "path"),
        sourceId == null ? null : UUID.fromString(sourceId),
        EventFields.nullableText(subject, "candidateId"),
        service,
        timing.get("attempt").asInt(),
        Instant.parse(EventFields.text(timing, "startedAt")),
        Instant.parse(EventFields.text(timing, "endedAt")),
        timing.get("durationMs").asDouble(),
        EventFields.text(timing, "status"),
        versions);
  }
}
