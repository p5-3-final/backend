package com.vlm.common.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox에 쌓인 이벤트를 Kafka로 발행한다. 토픽은 {@code event_type}, 메시지 키는 {@code aggregate_id}다.
 *
 * <p>여러 Pod가 동시에 돌아도 {@code FOR UPDATE SKIP LOCKED}로 행을 나눠 가져가 같은 행을 두 번 보내지 않는다. 전송은 됐지만 기록 전에
 * Pod가 죽으면 다시 보낼 수 있으므로(최소 한 번 전달) 소비자는 {@code eventId}로 중복을 흡수한다.
 */
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final KafkaOperations<String, String> kafka;
  private final OutboxProperties properties;

  public OutboxPublisher(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      KafkaOperations<String, String> kafka,
      OutboxProperties properties) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.kafka = kafka;
    this.properties = properties;
  }

  /** 발행하지 않은 이벤트를 한 묶음 발행한다. 주기 작업이 부르고, 테스트에서는 직접 부른다. */
  public void publishPending() {
    tx.executeWithoutResult(status -> lockPending().forEach(this::publish));
  }

  /** 종료할 때 전용 producer를 닫는다. */
  public void destroy() {
    kafka.getProducerFactory().reset();
  }

  private List<PendingEvent> lockPending() {
    return jdbc.query(
        "SELECT id, event_type, aggregate_id, payload::text AS payload, trace_parent"
            + " FROM outbox_events WHERE published_at IS NULL"
            + " ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED",
        (rs, rowNum) ->
            new PendingEvent(
                rs.getLong("id"),
                rs.getString("event_type"),
                rs.getString("aggregate_id"),
                rs.getString("payload"),
                rs.getString("trace_parent")),
        properties.batchSize());
  }

  private void publish(PendingEvent event) {
    ProducerRecord<String, String> record =
        new ProducerRecord<>(event.eventType(), event.aggregateId(), event.payload());
    if (event.traceParent() != null) {
      record.headers().add("traceparent", event.traceParent().getBytes(StandardCharsets.UTF_8));
    }
    try {
      kafka.send(record).get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
      jdbc.update(
          "UPDATE outbox_events SET published_at = now(), attempts = attempts + 1,"
              + " updated_at = now() WHERE id = ?",
          event.id());
    } catch (KafkaException | ExecutionException | TimeoutException e) {
      // 다음 주기에 같은 행(같은 eventId)을 다시 보낸다(SPEC-003 NFR-3).
      log.warn("Outbox 발행 실패, 다음 주기에 다시 시도: id={}, type={}", event.id(), event.eventType(), e);
      jdbc.update(
          "UPDATE outbox_events SET attempts = attempts + 1, updated_at = now() WHERE id = ?",
          event.id());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Outbox 발행 중 중단됐다", e);
    }
  }

  private record PendingEvent(
      long id,
      String eventType,
      String aggregateId,
      String payload,
      @Nullable String traceParent) {}
}
