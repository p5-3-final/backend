package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.*;
import com.vlm.common.messaging.*;
import com.vlm.pipeline.api.NormalizedEventParser;
import com.vlm.pipeline.application.*;
import com.vlm.pipeline.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest
@Testcontainers
@Import(DispatchKafkaTest.Topics.class)
@Sql(
    statements =
        "TRUNCATE normalized_receipts, normalized_job_plans, normalization_results, analysis_settings, chunks, jobs, dispatch_states, dispatch_users, chunk_executions, execution_slots, outbox_events RESTART IDENTITY CASCADE")
class DispatchKafkaTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

  @TestConfiguration(proxyBeanMethods = false)
  static class Topics {
    @Bean
    org.apache.kafka.clients.admin.NewTopic jobStatusTopic() {
      return new org.apache.kafka.clients.admin.NewTopic("job.status", 1, (short) 1);
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic requestTopic() {
      return new org.apache.kafka.clients.admin.NewTopic("chunk.requested", 1, (short) 1);
    }
  }

  @Autowired DispatchService dispatch;
  @Autowired DispatchRepository repository;
  @Autowired OutboxRecorder outbox;
  @Autowired com.vlm.pipeline.application.JobExecutionService executions;
  @Autowired NormalizedIntakeService intake;
  @Autowired NormalizedEventParser parser;
  @Autowired DispatchRecoveryService recovery;
  @Autowired JdbcTemplate jdbc;
  @Autowired TransactionTemplate tx;
  @Autowired OutboxPublisher publisher;
  @Autowired OutboxProperties properties;
  @Autowired ProducerFactory<?, ?> producerFactory;
  final ObjectMapper mapper = new ObjectMapper();
  final JsonSchema schema =
      JsonSchemaFactory.getInstance(
              SpecVersion.VersionFlag.V202012,
              b ->
                  b.schemaMappers(
                      m ->
                          m.mapPrefix(
                              "https://vlm.example/contracts/events/",
                              "classpath:contracts/events/")))
          .getSchema(
              SchemaLocation.of(
                  "https://vlm.example/contracts/events/chunk.requested.schema.json"));

  UUID prepare() throws Exception {
    return prepare(300000);
  }

  UUID prepare(long window) throws Exception {
    var e =
        (ObjectNode)
            mapper
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
    intake.receive(
        parser.parse(e.get("sourceId").asText(), e.toString()),
        new AnalysisSettings("dispatch-v1", "local", "infer-v1"),
        "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    UUID assigned =
        Objects.requireNonNull(
                tx.execute(
                    t ->
                        new DispatchService(
                                repository,
                                outbox,
                                new com.vlm.pipeline.config.DispatchProperties(
                                    false, 2, 1, 4, window, 1000, 100),
                                executions)
                            .dispatchOne()))
            .orElseThrow();
    // 이 테스트는 요청 Publisher의 재전달을 검증한다. 집계 발행은 실제 워커 통합 테스트가 검증한다.
    jdbc.update("UPDATE outbox_events SET published_at=now() WHERE event_type='job.status'");
    return assigned;
  }

  List<ConsumerRecord<String, String>> read(UUID execution, int expected) throws Exception {
    var records = new ArrayList<ConsumerRecord<String, String>>();
    try (var consumer =
        new KafkaConsumer<String, String>(
            Map.of(
                "bootstrap.servers",
                kafka.getBootstrapServers(),
                "group.id",
                "verify-" + UUID.randomUUID(),
                "enable.auto.commit",
                false,
                "key.deserializer",
                StringDeserializer.class,
                "value.deserializer",
                StringDeserializer.class))) {
      var partition = new TopicPartition("chunk.requested", 0);
      consumer.assign(List.of(partition));
      consumer.seekToBeginning(List.of(partition));
      long until = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (records.size() < expected && System.nanoTime() < until) {
        for (var r : consumer.poll(Duration.ofMillis(100)))
          if (mapper.readTree(r.value()).get("executionId").asText().equals(execution.toString()))
            records.add(r);
      }
    }
    return records;
  }

  @Test
  @DisplayName("[SPEC-010 R-6] 기존 Publisher가 계약·키·traceparent·정수 구간·프로필을 Kafka로 발행한다")
  void publishesContract() throws Exception {
    UUID execution = prepare();
    publisher.publishPending();
    var records = read(execution, 1);
    assertThat(records).hasSize(1);
    var r = records.getFirst();
    var node = mapper.readTree(r.value());
    assertThat(schema.validate(node)).isEmpty();
    assertThat(r.key()).isEqualTo(node.get("sourceId").asText());
    assertThat(new String(r.headers().lastHeader("traceparent").value(), StandardCharsets.UTF_8))
        .isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
    assertThat(node.get("requestId")).isEqualTo(node.get("executionId"));
    assertThat(node.at("/processingRange/endMs").asLong()).isEqualTo(65000);
    assertThat(node.at("/processingProfile/inferenceProfileVersion").asText())
        .isEqualTo("infer-v1");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='chunk.requested' AND published_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-010 R-7 R-9] 발행 실패 후 새 Publisher도 동일 ID·입력으로 재발행한다")
  void sendFailureAndRestart() throws Exception {
    UUID execution = prepare();
    var producer =
        new DefaultKafkaProducerFactory<String, String>(
            producerFactory.getConfigurationProperties(),
            new StringSerializer(),
            new StringSerializer());
    var operations = spy(new KafkaTemplate<>(producer));
    doThrow(new KafkaException("injected broker unavailable"))
        .when(operations)
        .send(org.mockito.ArgumentMatchers.<ProducerRecord<String, String>>any());
    var failed = new OutboxPublisher(jdbc, tx, operations, properties);
    try {
      failed.publishPending();
      assertThat(
              jdbc.queryForObject(
                  "SELECT attempts FROM outbox_events WHERE event_type='chunk.requested'",
                  Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM outbox_events WHERE event_type='chunk.requested' AND published_at IS NULL",
                  Integer.class))
          .isEqualTo(1);
      var restarted = new OutboxPublisher(jdbc, tx, new KafkaTemplate<>(producer), properties);
      restarted.publishPending();
      assertThat(read(execution, 1)).hasSize(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT attempts FROM outbox_events WHERE event_type='chunk.requested'",
                  Integer.class))
          .isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chunk_executions", Integer.class))
          .isEqualTo(1);
    } finally {
      producer.reset();
    }
  }

  @Test
  @DisplayName("[SPEC-010 R-7 R-9 R-11] Kafka 전송 후 DB 실패·재시작은 같은 실행을 재전달하고 검증 소비자가 한 번만 점유한다")
  void afterSendRollbackAndDeduplicate() throws Exception {
    UUID execution = prepare();
    jdbc.execute(
        "ALTER TABLE outbox_events ADD CONSTRAINT publish_failure CHECK(published_at IS NULL) NOT VALID");
    try {
      assertThatThrownBy(() -> publisher.publishPending())
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM outbox_events WHERE event_type='chunk.requested' AND published_at IS NULL",
                  Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT publish_failure");
    }
    var producer =
        new DefaultKafkaProducerFactory<String, String>(
            producerFactory.getConfigurationProperties(),
            new StringSerializer(),
            new StringSerializer());
    try {
      new OutboxPublisher(jdbc, tx, new KafkaTemplate<>(producer), properties).publishPending();
    } finally {
      producer.reset();
    }
    var records = read(execution, 2);
    assertThat(records).hasSize(2);
    assertThat(records.get(0).value()).isEqualTo(records.get(1).value());
    int starts = 0;
    for (var r : records) if (consume(r).equals("STARTED")) starts++;
    assertThat(starts).isEqualTo(1);
    var changed = (ObjectNode) mapper.readTree(records.getFirst().value());
    ((ObjectNode) changed.get("ownedRange")).put("endMs", 59999);
    var conflicting =
        new ConsumerRecord<String, String>(
            "chunk.requested", 0, 0, records.getFirst().key(), changed.toString());
    assertThatThrownBy(() -> consume(conflicting)).isInstanceOf(IllegalStateException.class);
    jdbc.update("UPDATE chunk_executions SET start_before=now() WHERE execution_id=?", execution);
    recovery.confirmStopped(execution, UUID.randomUUID(), "verification-consumer:stopped");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[SPEC-010 R-10] 지연 요청의 기한이 지나면 검증 소비자는 새 실행을 시작하지 않는다")
  void rejectsStaleRequest() throws Exception {
    UUID execution = prepare(1);
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                Boolean.TRUE.equals(
                    jdbc.queryForObject(
                        "SELECT start_before<=clock_timestamp() FROM chunk_executions WHERE execution_id=?",
                        Boolean.class,
                        execution)));
    // 발행 상태를 근거로 안전하다고 가정하지 않는다. 종료 확인 뒤 도착해도 새 실행은 금지된다.
    recovery.confirmStopped(execution, UUID.randomUUID(), "verification-consumer:never-started");
    publisher.publishPending();
    var records = read(execution, 1);
    assertThat(records).hasSize(1);
    assertThat(consume(records.getFirst())).isEqualTo("EXPIRED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM verification_claims", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isZero();
  }

  @BeforeEach
  void verificationLedger() {
    // 검증 소비자의 원장이다. 실제 worker의 저장소나 완료 연동을 구현한 것은 아니다.
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS verification_claims(execution_id UUID PRIMARY KEY,payload JSONB NOT NULL)");
    jdbc.execute("TRUNCATE verification_claims");
  }

  String consume(ConsumerRecord<String, String> record) throws Exception {
    var node = mapper.readTree(record.value());
    assertThat(schema.validate(node)).isEmpty();
    assertThat(record.key()).isEqualTo(node.get("sourceId").asText());
    // Docker VM과 호스트 시계가 절전 복귀 후 다를 수 있어 검증 소비자의 시각도 실제 DB에서 읽는다.
    // 기한 비교는 그대로 유지하며, 기한이 지난 요청은 아래 신규 점유를 하지 않는다.
    if (!repository.now().isBefore(Instant.parse(node.get("startBefore").asText())))
      return "EXPIRED";
    UUID execution = UUID.fromString(node.get("executionId").asText());
    return Objects.requireNonNull(
        tx.execute(
            status -> {
              int inserted =
                  jdbc.update(
                      "INSERT INTO verification_claims VALUES(?,CAST(? AS jsonb)) ON CONFLICT(execution_id) DO NOTHING",
                      execution,
                      record.value());
              if (!Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT payload=CAST(? AS jsonb) FROM verification_claims WHERE execution_id=?",
                      Boolean.class,
                      record.value(),
                      execution))) throw new IllegalStateException("Execution input conflicts");
              return inserted == 1 ? "STARTED" : "DUPLICATE";
            }));
  }
}
