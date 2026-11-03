package com.vlm.pipeline.api;

import com.vlm.pipeline.application.NormalizedIntakeService;
import com.vlm.pipeline.config.NormalizedIntakeProperties;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** 영상 바이트·추론·Outbox 없이 정규화 결과를 수신하는 transport adapter. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pipeline.intake.enabled", havingValue = "true")
public class NormalizedEventListener {
  private final NormalizedEventParser parser;
  private final NormalizedIntakeService intake;
  private final NormalizedIntakeProperties properties;

  @KafkaListener(
      id = "normalized-intake",
      topics = "video.normalized",
      groupId = "${pipeline.intake.group-id:pipeline-normalized-intake-v2}",
      containerFactory = "normalizedKafkaListenerContainerFactory")
  public void receive(ConsumerRecord<String, String> record) {
    var event = parser.parse(record.key(), record.value());
    var header = record.headers().lastHeader("traceparent");
    String traceparent = header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    if (traceparent != null && !traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}"))
      throw new IllegalArgumentException("Invalid traceparent");
    intake.receive(event, properties.settings(), traceparent);
  }
}
