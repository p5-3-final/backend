package com.vlm.media.event;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createSession;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
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

  @Test
  @DisplayName("[SPEC-017 R-11] 세션에 속한 영상이면 video.uploaded에 sessionId와 role을 담는다")
  void payloadHasSession() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String sessionId = createSession(mvc, ownerId, "방호벽 교체");
    JsonNode created =
        MediaApi.json(
            mvc.perform(
                asUser(
                    createVideoRequest(
                        "after.mp4",
                        1000,
                        "video/mp4",
                        Map.of("sessionId", sessionId, "role", "AFTER")),
                    ownerId)));
    complete(ownerId, created);

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));

    assertThat(ContractSchemas.validateEvent("video.uploaded.schema.json", payload)).isEmpty();
    assertThat(payload.get("sessionId").asText()).isEqualTo(sessionId);
    assertThat(payload.get("role").asText()).isEqualTo("AFTER");
  }

  @Test
  @DisplayName("[SPEC-017 R-12] 세션이 없는 영상이면 video.uploaded에 sessionId와 role이 없다")
  void payloadWithoutSession() throws Exception {
    completedVideo(UUID.randomUUID());

    JsonNode payload =
        json.readTree(jdbc.queryForObject("SELECT payload::text FROM outbox_events", String.class));

    assertThat(payload.has("sessionId")).isFalse();
    assertThat(payload.has("role")).isFalse();
  }

  private void complete(UUID ownerId, JsonNode created) throws Exception {
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    mvc.perform(asUser(completeRequest(created.at("/video/id").asText()), ownerId));
  }

  private String completedVideo(UUID ownerId) throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    String videoId = created.get("video").get("id").asText();
    mvc.perform(asUser(completeRequest(videoId), ownerId));
    return videoId;
  }
}
