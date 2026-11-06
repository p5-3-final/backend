package com.vlm.quality.alert.infra.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.alert.application.AlertIntakeService;
import com.vlm.quality.alert.application.AlertRaised;
import com.vlm.quality.alert.domain.AlertRecord;
import com.vlm.quality.alert.domain.AlertStatus;
import com.vlm.quality.shared.infra.messaging.EventFields;
import com.vlm.quality.shared.infra.messaging.EventSchemas;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** event-analyzer의 긴급 알림을 받는다(SPEC-029 R-28, SPEC-023 R-21~R-23). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.quality.consumers-enabled",
    havingValue = "true",
    matchIfMissing = true)
class AlertRaisedListener {

  private static final String SCHEMA = "events/alert.raised.schema.json";

  private final EventSchemas schemas;
  private final AlertIntakeService intake;

  @KafkaListener(
      id = "quality-alert",
      topics = "alert.raised",
      groupId = "quality-service",
      containerFactory = "qualityKafkaListenerContainerFactory")
  void receive(ConsumerRecord<String, String> record) {
    intake.intake(parse(schemas.readValid(SCHEMA, record.value())));
  }

  private static AlertRaised parse(JsonNode event) {
    return new AlertRaised(
        new AlertRecord(
            EventFields.uuid(event, "candidateId"),
            EventFields.uuid(event, "sourceId"),
            EventFields.uuid(event, "cameraId"),
            EventFields.text(event, "candidateEventType"),
            EventFields.text(event, "severity"),
            AlertStatus.fromWire(EventFields.text(event, "status")),
            EventFields.instant(event, "occurredAt"),
            EventFields.nullableText(event, "dismissReason"),
            EventFields.texts(event, "evidenceFrameKeys")),
        EventFields.uuid(event, "eventId"),
        EventFields.uuid(event, "jobId"),
        EventFields.videoMs(event, "observedMs"),
        EventFields.text(event, "taxonomyVersion"));
  }
}
