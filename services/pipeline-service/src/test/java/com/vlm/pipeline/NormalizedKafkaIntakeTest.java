package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.pipeline.api.NormalizedEventParser;
import com.vlm.pipeline.application.NormalizedIntakeService;
import com.vlm.pipeline.domain.*;
import com.vlm.pipeline.infra.persistence.JdbcNormalizedReceiptRepository;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
      "pipeline.intake.enabled=true",
      "pipeline.intake.profile-version=kafka-test-v1",
      "pipeline.intake.inference-profile-version=test-infer-v1",
      "pipeline.intake.group-id=spec008-kafka",
      "pipeline.intake.retry-interval-ms=100",
      "pipeline.intake.retry-attempts=1",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@Testcontainers
@Import(NormalizedKafkaIntakeTest.Topics.class)
@Sql(
    statements =
        "TRUNCATE normalized_receipts, normalized_job_plans, normalization_results, analysis_settings, chunks, jobs RESTART IDENTITY CASCADE")
class NormalizedKafkaIntakeTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

  @TestConfiguration(proxyBeanMethods = false)
  static class Topics {
    @Bean
    org.apache.kafka.clients.admin.NewTopic normalizedTopic() {
      return new org.apache.kafka.clients.admin.NewTopic("video.normalized", 1, (short) 1);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaListenerEndpointRegistry registry;
  @Autowired NormalizedEventParser parser;
  @Autowired NormalizedIntakeService intake;
  @MockitoSpyBean JdbcNormalizedReceiptRepository receipts;

  @BeforeEach
  void assigned() {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        Objects.requireNonNull(registry.getListenerContainer("normalized-intake"))
                            .getAssignedPartitions())
                    .hasSize(1));
  }

  ObjectNode event() throws Exception {
    ObjectNode e =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    Objects.requireNonNull(
                        getClass()
                            .getResourceAsStream(
                                "/contracts/events/video.normalized.v2.schema.json")))
                .get("examples")
                .get(0)
                .deepCopy();
    e.put("eventId", UUID.randomUUID().toString());
    e.put("sourceId", UUID.randomUUID().toString());
    e.put("normalizationId", UUID.randomUUID().toString());
    String prefix =
        "sources/"
            + e.get("sourceId").asText()
            + "/normalizations/"
            + e.get("normalizationId").asText()
            + "/";
    e.put("normalizedKey", prefix + "normalized.mp4");
    e.put("hlsPlaylistKey", prefix + "hls/index.m3u8");
    return e;
  }

  RecordMetadata send(ObjectNode e) throws Exception {
    try (var p =
        new KafkaProducer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafka.getBootstrapServers(),
                "key.serializer",
                StringSerializer.class,
                "value.serializer",
                StringSerializer.class))) {
      var record =
          new ProducerRecord<String, String>(
              "video.normalized", e.get("sourceId").asText(), e.toString());
      record
          .headers()
          .add(
              "traceparent",
              "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return p.send(record).get(10, TimeUnit.SECONDS);
    }
  }

  long committed() throws Exception {
    try (var admin = Admin.create(Map.of("bootstrap.servers", kafka.getBootstrapServers()))) {
      var value =
          admin
              .listConsumerGroupOffsets("spec008-kafka")
              .partitionsToOffsetAndMetadata()
              .get(10, TimeUnit.SECONDS)
              .get(new TopicPartition("video.normalized", 0));
      return value == null ? -1 : value.offset();
    }
  }

  void completed(RecordMetadata record) {
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(committed()).isGreaterThan(record.offset()));
  }

  int count(String table) {
    return Objects.requireNonNull(
        jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
  }

  @Test
  @DisplayName("[SPEC-008 R-3 R-8 R-10] 실제 Kafka 정상 수신·재전달은 DB 커밋 후 offset을 진행한다")
  void normalReplay() throws Exception {
    var e = event();
    var first = send(e);
    completed(first);
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT trace_parent FROM normalized_receipts", String.class))
        .isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    completed(send(e));
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-008 R-9 R-10] 저장 실패 동안 offset이 진행하지 않고 장애 해소 후 재전달로 복구한다")
  void rollbackRecovery() throws Exception {
    jdbc.execute("ALTER TABLE normalized_receipts ADD CONSTRAINT kafka_failure CHECK(false)");
    try {
      var e = event();
      var sent = send(e);
      await()
          .atMost(Duration.ofSeconds(20))
          .untilAsserted(
              () ->
                  verify(receipts, atLeastOnce())
                      .saveReceipt(any(), nullable(UUID.class), nullable(String.class)));
      assertThat(committed()).isLessThanOrEqualTo(sent.offset());
      assertThat(count("jobs")).isZero();
      assertThat(count("normalization_results")).isZero();
      jdbc.execute("ALTER TABLE normalized_receipts DROP CONSTRAINT kafka_failure");
      completed(sent);
      assertThat(count("jobs")).isEqualTo(1);
      assertThat(count("normalized_receipts")).isEqualTo(1);
    } finally {
      jdbc.execute("ALTER TABLE normalized_receipts DROP CONSTRAINT IF EXISTS kafka_failure");
    }
  }

  @Test
  @DisplayName("[SPEC-008 R-8 R-10] DB 커밋 후 ack 전 중단을 재현해도 재전달로 중복 저장하지 않는다")
  void afterCommitRecovery() throws Exception {
    var e = event();
    UUID id = UUID.fromString(e.get("eventId").asText());
    var once = new AtomicBoolean();
    doAnswer(
            call -> {
              call.callRealMethod();
              NormalizedVideo input = call.getArgument(0);
              if (input.eventId().equals(id) && once.compareAndSet(false, true))
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                      @Override
                      public void afterCommit() {
                        throw new IllegalStateException(
                            "Test interruption after commit before ack");
                      }
                    });
              return null;
            })
        .when(receipts)
        .saveReceipt(any(), nullable(UUID.class), nullable(String.class));
    var sent = send(e);
    completed(sent);
    assertThat(once).isTrue();
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(1);
    verify(receipts, atLeast(2)).findReceipt(any());
  }
}
