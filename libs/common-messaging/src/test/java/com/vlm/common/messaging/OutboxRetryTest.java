package com.vlm.common.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@Import(OutboxTestApplication.Containers.class)
class OutboxRetryTest {

  @Autowired OutboxRecorder recorder;
  @Autowired TransactionTemplate tx;
  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaConnectionDetails kafkaConnection;
  @Autowired OutboxProperties properties;
  @Autowired ProducerFactory<?, ?> producerFactory;

  private KafkaTemplate<String, String> kafka;

  @AfterEach
  void closeProducer() {
    if (kafka != null) {
      kafka.getProducerFactory().reset();
    }
  }

  @Test
  @DisplayName("[SPEC-003 NFR-3] 발행에 실패하면 다음 시도에서 같은 이벤트를 한 번 다시 보낸다")
  void retriesFailedEventWithSameEventId() {
    // given: 첫 전송만 실패하는 Kafka
    kafka =
        spy(
            new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(
                    producerFactory.getConfigurationProperties(),
                    new StringSerializer(),
                    new StringSerializer())));
    doThrow(new KafkaException("broker down"))
        .doCallRealMethod()
        .when(kafka)
        .send(ArgumentMatchers.<ProducerRecord<String, String>>any());
    OutboxPublisher publisher = new OutboxPublisher(jdbc, tx, kafka, properties);
    String topic = OutboxTestSupport.newTopic();
    UUID eventId = UUID.randomUUID();
    tx.executeWithoutResult(
        status -> recorder.record(eventId, topic, UUID.randomUUID(), Map.of("eventId", eventId)));

    // when
    publisher.publishPending();
    Map<String, Object> afterFailure =
        jdbc.queryForMap(
            "SELECT attempts, published_at FROM outbox_events WHERE event_id = ?", eventId);
    publisher.publishPending();

    // then
    assertThat(afterFailure.get("attempts")).isEqualTo(1);
    assertThat(afterFailure.get("published_at")).isNull();
    List<ConsumerRecord<String, String>> records =
        OutboxTestSupport.readTopic(
            String.join(",", kafkaConnection.getBootstrapServers()),
            topic,
            Duration.ofSeconds(10),
            2);
    assertThat(records).hasSize(1);
    assertThat(records.get(0).value()).contains(eventId.toString());
  }
}
