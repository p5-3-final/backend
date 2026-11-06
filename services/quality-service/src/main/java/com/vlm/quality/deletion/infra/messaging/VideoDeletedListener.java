package com.vlm.quality.deletion.infra.messaging;

import static com.vlm.quality.shared.infra.messaging.EventFields.instant;
import static com.vlm.quality.shared.infra.messaging.EventFields.sourceIdMatchingKey;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.deletion.application.SourceDeletionService;
import com.vlm.quality.shared.infra.messaging.EventSchemas;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * media-service의 영상 삭제를 받는다(SPEC-029 R-16).
 *
 * <p>보존 동의(retention)가 있어도 quality-service는 등급 (b)로 남길 데이터가 없어 모두 지운다. 확정 Golden Set은 삭제 전파 대상이
 * 아니다(ADR-0011).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.quality.consumers-enabled",
    havingValue = "true",
    matchIfMissing = true)
class VideoDeletedListener {

  private static final String SCHEMA = "events/video.deleted.schema.json";

  private final EventSchemas schemas;
  private final SourceDeletionService deletions;

  @KafkaListener(
      id = "quality-deletion",
      topics = "video.deleted",
      groupId = "quality-service",
      containerFactory = "qualityKafkaListenerContainerFactory")
  void receive(ConsumerRecord<String, String> record) {
    JsonNode event = schemas.readValid(SCHEMA, record.value());
    deletions.delete(sourceIdMatchingKey(event, record.key()), instant(event, "occurredAt"));
  }
}
