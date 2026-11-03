package com.vlm.media.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import com.vlm.media.support.IntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

@IntegrationTest
class NormalizationKafkaTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired VideoRepository videos;
  @Autowired JdbcTemplate jdbc;
  @Autowired KafkaListenerEndpointRegistry registry;
  @Autowired KafkaConnectionDetails kafka;

  @BeforeEach
  void listening() {
    await()
        .atMost(WAIT)
        .untilAsserted(
            () ->
                assertThat(
                        Objects.requireNonNull(registry.getListenerContainer("media-normalization"))
                            .getAssignedPartitions())
                    .hasSize(2));
  }

  @Test
  @DisplayName("[SPEC-007 R-15, R-16] 시작 신호와 성공 결과를 받으면 영상이 정규화 중을 거쳐 준비됨이 된다")
  void startThenReady() throws IOException {
    UUID id = uploadedVideo();

    send("video.normalizing", id, normalizing(id).toString());
    await().atMost(WAIT).until(() -> "NORMALIZING".equals(status(id)));
    send("video.normalized", id, ready(id).toString());

    await().atMost(WAIT).until(() -> "READY".equals(status(id)));
    assertThat(row(id)).containsEntry("duration_ms", 125_001L);
  }

  @Test
  @DisplayName("[SPEC-007] 계약을 어긴 메시지는 건너뛰고 뒤 메시지를 처리한다")
  void invalidMessageIsSkipped() throws IOException {
    UUID id = uploadedVideo();

    send("video.normalized", id, "{not json");
    send("video.normalized", id, ready(id).put("durationSec", 1.0).toString());
    send("video.normalized", id, ready(id).toString());

    await().atMost(WAIT).until(() -> "READY".equals(status(id)));
  }

  @Test
  @DisplayName("[SPEC-007] 저장이 일시적으로 실패하면 건너뛰지 않고 다시 시도해 처리한다")
  void transientFailureIsRetried() throws IOException {
    UUID id = uploadedVideo();
    // 모든 상태 변경을 막아 DB 장애를 흉내 낸다.
    jdbc.execute(
        "ALTER TABLE videos ADD CONSTRAINT kafka_test_failure CHECK (status = 'UPLOADED') NOT VALID");
    try {
      send("video.normalized", id, ready(id).toString());
      // 다시 시도하는 동안에는 그대로다.
      await().pollDelay(Duration.ofSeconds(3)).until(() -> "UPLOADED".equals(status(id)));
    } finally {
      jdbc.execute("ALTER TABLE videos DROP CONSTRAINT kafka_test_failure");
    }

    await().atMost(WAIT).until(() -> "READY".equals(status(id)));
  }

  private UUID uploadedVideo() {
    Video video =
        Video.startUpload(UUID.randomUUID(), "a.mp4", 1000, "video/mp4", NOW, Duration.ofHours(24));
    video.completeUpload(NOW);
    return videos.save(video).getPublicId();
  }

  private static ObjectNode normalizing(UUID id) throws IOException {
    return example("video.normalizing.schema.json", 0).put("sourceId", id.toString());
  }

  private static ObjectNode ready(UUID id) throws IOException {
    ObjectNode event = example("video.normalized.v2.schema.json", 0);
    UUID normalization = UUID.randomUUID();
    String base = "sources/" + id + "/normalizations/" + normalization + "/";
    return event
        .put("eventId", UUID.randomUUID().toString())
        .put("sourceId", id.toString())
        .put("normalizationId", normalization.toString())
        .put("normalizedKey", base + "normalized.mp4")
        .put("hlsPlaylistKey", base + "hls/index.m3u8");
  }

  private static ObjectNode example(String schemaFile, int index) throws IOException {
    Path file = Path.of(System.getProperty("vlm.contracts.dir"), "events", schemaFile);
    return (ObjectNode) JSON.readTree(Files.readString(file)).get("examples").get(index).deepCopy();
  }

  private void send(String topic, UUID key, String value) {
    try (var producer =
        new KafkaProducer<String, String>(
            Map.of(
                "bootstrap.servers",
                String.join(",", kafka.getProducer().getBootstrapServers()),
                "key.serializer",
                StringSerializer.class,
                "value.serializer",
                StringSerializer.class))) {
      producer.send(new ProducerRecord<>(topic, key.toString(), value)).get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("send failed", e);
    }
  }

  private String status(UUID id) {
    return jdbc.queryForObject("SELECT status FROM videos WHERE public_id = ?", String.class, id);
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("SELECT * FROM videos WHERE public_id = ?", id);
  }
}
