package com.vlm.quality.deletion.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.support.Events;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class VideoDeletedIntakeTest {

  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;
  @Autowired GoldenFixtures golden;
  @Autowired ReviewFixtures reviews;

  @Test
  @DisplayName("[SPEC-029 R-16] video.deleted를 받으면 그 영상의 검토·초안 항목을 지우고 확정 버전·매니페스트·프레임은 남긴다")
  void deletesItemsButKeepsFrozenVersion() throws Exception {
    UUID setId = golden.create("delete-set");
    UUID source = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    golden.addEntry(
        setId,
        reviews.labeled(
            source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT));
    UUID kept = reviews.reviewRequired(other);
    JsonNode version = golden.freeze(setId);

    send("video.deleted", Events.videoDeleted(source));

    await()
        .atMost(WAIT)
        .until(() -> count("SELECT count(*) FROM review_items WHERE source_id = ?", source) == 0);
    assertThat(count("SELECT count(*) FROM golden_draft_entries", null)).isZero();
    assertThat(count("SELECT count(*) FROM review_items WHERE item_id = ?", kept)).isEqualTo(1);
    assertThat(count("SELECT count(*) FROM golden_set_versions", null)).isEqualTo(1);
    String manifestKey = version.get("manifestKey").asText();
    assertThat(StorageFixtures.goldenObject(manifestKey)).isNotEmpty();
    assertThat(StorageFixtures.goldenKeys("golden/" + setId + "/v1/frames/")).hasSize(1);
  }

  @Test
  @DisplayName("[SPEC-029 R-16] 삭제 뒤에 온 event.analyzed로는 항목을 다시 만들지 않는다")
  void ignoresLateAnalyzedEvent() {
    UUID source = UUID.randomUUID();
    send("video.deleted", Events.videoDeleted(source));
    await()
        .atMost(WAIT)
        .until(
            () -> count("SELECT count(*) FROM deleted_sources WHERE source_id = ?", source) == 1);

    UUID late = UUID.randomUUID();
    send("event.analyzed", Events.reviewRequired(late, source));
    UUID marker = UUID.randomUUID();
    send("event.analyzed", Events.reviewRequired(marker, UUID.randomUUID()));
    await()
        .atMost(WAIT)
        .until(() -> count("SELECT count(*) FROM review_items WHERE event_id = ?", marker) == 1);

    assertThat(count("SELECT count(*) FROM review_items WHERE event_id = ?", late)).isZero();
  }

  private void send(String topic, ObjectNode event) {
    kafka.send(topic, event.get("sourceId").asText(), event.toString()).join();
  }

  private int count(String sql, Object arg) {
    Integer count =
        arg == null
            ? jdbc.queryForObject(sql, Integer.class)
            : jdbc.queryForObject(sql, Integer.class, arg);
    return count == null ? 0 : count;
  }
}
