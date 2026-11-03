package com.vlm.media.api;

import com.vlm.media.application.NormalizationEventService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** normalize 워커의 시작 신호·결과를 받아 영상 상태를 바꾼다(SPEC-007 R-15~R-19). */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "vlm.media.normalization.consumer-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class NormalizationEventListener {

  private final NormalizationEventParser parser;
  private final NormalizationEventService service;

  // 두 토픽을 한 리스너로 받는다. 키가 같은 sourceId라도 토픽 사이 순서는 보장되지 않으므로
  // 늦게 온 시작 신호는 서비스의 상태 전이 규칙이 무시한다(R-18).
  @KafkaListener(
      id = "media-normalization",
      topics = {"video.normalizing", "video.normalized"},
      groupId = "media-service",
      containerFactory = "normalizationKafkaListenerContainerFactory")
  public void receive(ConsumerRecord<String, String> record) {
    if ("video.normalizing".equals(record.topic())) {
      service.onNormalizing(parser.parseNormalizing(record.key(), record.value()));
    } else {
      service.onNormalized(parser.parseNormalized(record.key(), record.value()));
    }
  }
}
