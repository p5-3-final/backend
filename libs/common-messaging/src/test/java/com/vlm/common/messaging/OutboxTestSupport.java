package com.vlm.common.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/** 테스트에서 토픽 하나를 끝까지 읽는다. */
final class OutboxTestSupport {

  private OutboxTestSupport() {}

  static String newTopic() {
    return "test.event." + UUID.randomUUID();
  }

  static List<ConsumerRecord<String, String>> readTopic(
      String bootstrapServers, String topic, Duration timeout, int expected) {
    Map<String, Object> props =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG,
            "test-" + UUID.randomUUID(),
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
            "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    List<ConsumerRecord<String, String>> records = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      consumer.subscribe(List.of(topic));
      long deadline = System.nanoTime() + timeout.toNanos();
      while (System.nanoTime() < deadline && records.size() < expected) {
        consumer.poll(Duration.ofMillis(200)).forEach(records::add);
      }
    }
    return records;
  }
}
