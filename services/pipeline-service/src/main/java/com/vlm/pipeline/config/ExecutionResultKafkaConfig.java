package com.vlm.pipeline.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pipeline.results.enabled", havingValue = "true")
public class ExecutionResultKafkaConfig {
  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String>
      executionResultsKafkaListenerContainerFactory(
          KafkaProperties kafka,
          org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails connection,
          NormalizedIntakeProperties properties) {
    return PipelineKafkaFactories.create(kafka, connection, properties);
  }
}
