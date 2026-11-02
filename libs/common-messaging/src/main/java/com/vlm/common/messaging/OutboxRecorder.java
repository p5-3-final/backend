package com.vlm.common.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 상태 변경과 같은 트랜잭션에 이벤트를 Outbox에 기록한다. 발행은 {@link OutboxPublisher}가 따로 한다.
 *
 * <p>각 서비스는 마이그레이션으로 {@code outbox_events} 테이블을 같은 구조로 만든다.
 */
public class OutboxRecorder {

  private final JdbcTemplate jdbc;
  private final ObjectMapper objectMapper;
  private final TraceParentProvider traceParent;

  public OutboxRecorder(
      JdbcTemplate jdbc, ObjectMapper objectMapper, TraceParentProvider traceParent) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.traceParent = traceParent;
  }

  /**
   * 이벤트를 기록한다. 진행 중인 트랜잭션이 없으면 예외를 던진다.
   *
   * @param eventId 이벤트 ID. payload의 {@code eventId}와 같아야 하며 재발행해도 바뀌지 않는다.
   * @param eventType 토픽 이름(예: {@code video.uploaded})
   * @param aggregateId Kafka 메시지 키(예: sourceId)
   * @param payload JSON으로 직렬화할 이벤트
   */
  public void record(UUID eventId, String eventType, UUID aggregateId, Object payload) {
    // 트랜잭션 밖에서 기록하면 상태 변경 없이 이벤트만 나가거나 그 반대가 생긴다.
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Outbox는 상태 변경과 같은 트랜잭션 안에서만 기록한다.");
    }
    jdbc.update(
        "INSERT INTO outbox_events (event_id, event_type, aggregate_id, payload, trace_parent)"
            + " VALUES (?, ?, ?, CAST(? AS jsonb), ?)",
        eventId,
        eventType,
        aggregateId,
        toJson(payload),
        traceParent.current().orElse(null));
  }

  private String toJson(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("이벤트를 JSON으로 바꿀 수 없다: " + payload.getClass(), e);
    }
  }
}
