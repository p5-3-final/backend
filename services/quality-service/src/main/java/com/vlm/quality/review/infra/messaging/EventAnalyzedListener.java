package com.vlm.quality.review.infra.messaging;

import com.vlm.quality.review.application.ReviewIntakeService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** event-analyzer의 판정 결과를 검토 큐로 받는다(SPEC-029 R-1~R-3, R-27). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.quality.consumers-enabled",
    havingValue = "true",
    matchIfMissing = true)
class EventAnalyzedListener {

  private final EventAnalyzedParser parser;
  private final ReviewIntakeService intake;

  @KafkaListener(
      id = "quality-review",
      topics = "event.analyzed",
      groupId = "quality-service",
      containerFactory = "qualityKafkaListenerContainerFactory")
  void receive(ConsumerRecord<String, String> record) {
    intake.intake(parser.parse(record.key(), record.value()));
  }
}
