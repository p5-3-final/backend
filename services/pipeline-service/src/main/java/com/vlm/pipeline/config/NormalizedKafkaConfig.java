package com.vlm.pipeline.config;

import java.util.HashMap;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

/** DB 커밋이 끝난 listener 반환 뒤에만 RECORD offset을 동기 커밋한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pipeline.intake.enabled", havingValue = "true")
public class NormalizedKafkaConfig {
  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> normalizedKafkaListenerContainerFactory(
      KafkaProperties kafka,
      org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails connection,
      NormalizedIntakeProperties properties) {
    properties.settings();
    var consumer = new HashMap<>(kafka.buildConsumerProperties(null));
    consumer.put(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, connection.getConsumer().getBootstrapServers());
    consumer.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
    consumer.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumer.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumer.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumer));
    factory.setConcurrency(1);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    factory.getContainerProperties().setSyncCommits(true);
    factory.getContainerProperties().setObservationEnabled(true);
    // 회수 시에도 예외를 던져 불량 입력을 성공 offset으로 넘기지 않는다. DLQ/수동 해소는 후속 범위다.
    var errors =
        new DefaultErrorHandler(
            (record, error) -> {
              throw new org.springframework.kafka.KafkaException(
                  "Normalized intake remains uncommitted", error);
            },
            new FixedBackOff(properties.retryIntervalMs(), properties.retryAttempts()));
    errors.setAckAfterHandle(false);
    errors.setCommitRecovered(false);
    factory.setCommonErrorHandler(errors);
    return factory;
  }
}
