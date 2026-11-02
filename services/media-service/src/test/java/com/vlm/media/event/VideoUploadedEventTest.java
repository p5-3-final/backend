package com.vlm.media.event;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VideoUploadedEventTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  private final ObjectMapper json = new ObjectMapper();

  @Test
  @DisplayName("[SPEC-003 R-9] 업로드 완료로 바뀌면 video.uploaded를 한 번 기록한다")
  void recordsVideoUploadedOnce() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String videoId = completedVideo(ownerId);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT event_type, aggregate_id::text AS aggregate_id FROM outbox_events");

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0))
        .containsEntry("event_type", "video.uploaded")
        .containsEntry("aggregate_id", videoId);
  }

  @Test
  @DisplayName("[SPEC-003 R-21] video.uploaded payload는 계약 스키마와 맞고 영상 정보를 담는다")
  void payloadMatchesContract() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String videoId = completedVideo(ownerId);

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));

    assertThat(ContractSchemas.validateEvent("video.uploaded.schema.json", payload)).isEmpty();
    assertThat(payload.get("sourceId").asText()).isEqualTo(videoId);
    assertThat(payload.get("ownerId").asText()).isEqualTo(ownerId.toString());
    assertThat(payload.get("storageKey").asText()).isEqualTo("sources/" + videoId + "/original");
    assertThat(payload.get("sizeBytes").asLong()).isEqualTo(1000);
    assertThat(payload.get("contentType").asText()).isEqualTo("video/mp4");
  }

  private String completedVideo(UUID ownerId) throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    String videoId = created.get("video").get("id").asText();
    mvc.perform(asUser(completeRequest(videoId), ownerId));
    return videoId;
  }
}
