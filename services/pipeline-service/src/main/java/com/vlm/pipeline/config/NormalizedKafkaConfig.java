package com.vlm.pipeline.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pipeline.intake.enabled", havingValue = "true")
public class NormalizedKafkaConfig {
  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> normalizedKafkaListenerContainerFactory(
      KafkaProperties kafka,
      org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails connection,
      NormalizedIntakeProperties properties) {
    properties.settings();
    return PipelineKafkaFactories.create(kafka, connection, properties);
  }
}
