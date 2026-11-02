package com.vlm.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/** Outbox 기록·발행 빈을 등록한다. */
@AutoConfiguration(
    after = {
      KafkaAutoConfiguration.class,
      JacksonAutoConfiguration.class,
      JdbcTemplateAutoConfiguration.class,
      DataSourceTransactionManagerAutoConfiguration.class
    })
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

  @Bean
  OutboxRecorder outboxRecorder(
      JdbcTemplate jdbc,
      ObjectProvider<ObjectMapper> objectMapper,
      ObjectProvider<TraceParentProvider> traceParentProvider) {
    // 서비스의 ObjectMapper(시각을 ISO-8601 문자열로 쓰는 설정)를 따른다. 없으면 같은 방식으로 만든다.
    ObjectMapper mapper =
        objectMapper.getIfAvailable(
            () ->
                JsonMapper.builder()
                    .findAndAddModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .build());
    return new OutboxRecorder(
        jdbc, mapper, traceParentProvider.getIfAvailable(() -> TraceParentProvider.NONE));
  }

  @Bean(destroyMethod = "destroy")
  OutboxPublisher outboxPublisher(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      ProducerFactory<?, ?> producerFactory,
      OutboxProperties properties) {
    // Boot가 만든 producer 설정(브로커 주소, 보안 설정)을 그대로 쓰되 직렬화는 문자열로 고정한다.
    // 빈으로 등록하지 않는 이유: ProducerFactory·KafkaTemplate 빈을 더 만들면 Boot 기본 빈이 물러나 서비스 설정이 바뀐다.
    DefaultKafkaProducerFactory<String, String> outboxProducerFactory =
        new DefaultKafkaProducerFactory<>(
            producerFactory.getConfigurationProperties(),
            new StringSerializer(),
            new StringSerializer());
    return new OutboxPublisher(jdbc, tx, new KafkaTemplate<>(outboxProducerFactory), properties);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(Tracer.class)
  static class TracingConfiguration {

    @Bean
    TraceParentProvider tracerTraceParentProvider(ObjectProvider<Tracer> tracer) {
      return () ->
          Optional.ofNullable(tracer.getIfAvailable())
              .map(Tracer::currentSpan)
              .map(
                  span -> {
                    TraceContext context = span.context();
                    // W3C Trace Context: version-traceId-spanId-flags
                    return "00-"
                        + context.traceId()
                        + "-"
                        + context.spanId()
                        + (Boolean.TRUE.equals(context.sampled()) ? "-01" : "-00");
                  });
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(
      name = "vlm.outbox.scheduling-enabled",
      havingValue = "true",
      matchIfMissing = true)
  static class SchedulingConfiguration {

    @Bean
    OutboxPublishSchedule outboxPublishSchedule(OutboxPublisher publisher) {
      return new OutboxPublishSchedule(publisher);
    }
  }

  /** 주기 발행. 여러 Pod가 동시에 돌아도 행 잠금으로 같은 행을 나눠 가진다. */
  static class OutboxPublishSchedule {

    private final OutboxPublisher publisher;

    OutboxPublishSchedule(OutboxPublisher publisher) {
      this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${vlm.outbox.publish-interval-ms:1000}")
    void publish() {
      publisher.publishPending();
    }
  }
}
