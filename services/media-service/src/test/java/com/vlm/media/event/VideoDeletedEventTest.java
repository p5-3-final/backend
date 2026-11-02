package com.vlm.media.event;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VideoDeletedEventTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-19] 삭제로 바뀌면 video.deleted를 한 번 기록한다")
  void recordsVideoDeletedOnce() throws Exception {
    String videoId = newVideo();

    mvc.perform(asUser(deleteRequest(videoId), ownerId));
    mvc.perform(asUser(deleteRequest(videoId), ownerId));

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT event_type, aggregate_id::text AS aggregate_id FROM outbox_events");
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("event_type", "video.deleted")
        .containsEntry("aggregate_id", videoId);
  }

  @Test
  @DisplayName("[SPEC-003 R-21] video.deleted payload는 계약 스키마와 맞고 ADMIN이 지워도 소유자 ID를 담는다")
  void payloadMatchesContract() throws Exception {
    String videoId = newVideo();

    mvc.perform(asAdmin(deleteRequest(videoId), UUID.randomUUID()));

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));
    assertThat(ContractSchemas.validateEvent("video.deleted.schema.json", payload)).isEmpty();
    assertThat(payload.get("eventType").asText()).isEqualTo("video.deleted");
    assertThat(payload.get("sourceId").asText()).isEqualTo(videoId);
    assertThat(payload.get("ownerId").asText()).isEqualTo(ownerId.toString());
  }

  private String newVideo() throws Exception {
    return createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
  }
}
