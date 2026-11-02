package com.vlm.common.messaging;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/** 라이브러리 통합 테스트용 애플리케이션. 서비스처럼 PostgreSQL과 Kafka를 붙인다. */
@SpringBootApplication
class OutboxTestApplication {

  @TestConfiguration(proxyBeanMethods = false)
  static class Containers {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
      return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
      return new KafkaContainer("apache/kafka:4.3.2");
    }
  }
}
