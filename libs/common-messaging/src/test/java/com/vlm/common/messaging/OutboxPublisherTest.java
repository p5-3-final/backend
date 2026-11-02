package com.vlm.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "vlm.outbox.scheduling-enabled=true")
@AutoConfigureObservability(metrics = false)
@Import(OutboxTestApplication.Containers.class)
class OutboxPublisherTest {

  @Autowired OutboxRecorder recorder;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaConnectionDetails kafkaConnection;
  @Autowired Tracer tracer;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("[SPEC-003 NFR-3] 커밋된 이벤트를 10초 안에 키·payload·traceparent와 함께 발행한다")
  void publishesCommittedEventWithinTenSeconds() throws Exception {
    String topic = OutboxTestSupport.newTopic();
    UUID eventId = UUID.randomUUID();
    UUID aggregateId = UUID.randomUUID();
    Span span = tracer.nextSpan().name("test").start();
    String expectedTraceParent =
        "00-"
            + span.context().traceId()
            + "-"
            + span.context().spanId()
            + (Boolean.TRUE.equals(span.context().sampled()) ? "-01" : "-00");
    Tracer.SpanInScope scope = tracer.withSpan(span);
    try {
      tx.executeWithoutResult(
          status -> recorder.record(eventId, topic, aggregateId, Map.of("eventId", eventId)));
    } finally {
      scope.close();
      span.end();
    }

    List<ConsumerRecord<String, String>> records =
        OutboxTestSupport.readTopic(bootstrapServers(), topic, Duration.ofSeconds(10), 1);

    assertThat(records).hasSize(1);
    ConsumerRecord<String, String> record = records.get(0);
    assertThat(record.key()).isEqualTo(aggregateId.toString());
    // JSONB로 저장하면서 공백이 정규화되므로 문자열이 아니라 JSON 내용으로 비교한다.
    assertThat(objectMapper.readTree(record.value()))
        .isEqualTo(objectMapper.valueToTree(Map.of("eventId", eventId)));
    Header traceParent = record.headers().lastHeader("traceparent");
    assertThat(traceParent).isNotNull();
    assertThat(new String(traceParent.value(), StandardCharsets.UTF_8))
        .isEqualTo(expectedTraceParent);
  }

  @Test
  @DisplayName("[SPEC-003 NFR-3] 롤백된 트랜잭션의 이벤트는 기록되지 않는다")
  void doesNotKeepRolledBackEvent() {
    String topic = OutboxTestSupport.newTopic();

    tx.executeWithoutResult(
        status -> {
          recorder.record(UUID.randomUUID(), topic, UUID.randomUUID(), Map.of("n", 1));
          status.setRollbackOnly();
        });

    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE event_type = ?", Integer.class, topic);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("[SPEC-003 NFR-3] 트랜잭션 밖에서 기록하면 예외를 던진다")
  void rejectsRecordOutsideTransaction() {
    assertThatThrownBy(
            () ->
                recorder.record(
                    UUID.randomUUID(), OutboxTestSupport.newTopic(), UUID.randomUUID(), Map.of()))
        .isInstanceOf(IllegalStateException.class);
  }

  private String bootstrapServers() {
    return String.join(",", kafkaConnection.getBootstrapServers());
  }
}
