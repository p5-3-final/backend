package com.vlm.media.retention.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.retainedVideosRequest;
import static com.vlm.media.support.MediaApi.revokeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.media.support.ContractSchemas;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RetainedVideosApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();
  private final ObjectMapper json = new ObjectMapper();

  @Test
  @DisplayName("[SPEC-018 R-13] 보존 중인 삭제 영상은 내 것만 삭제 최신순으로 영상 ID·삭제 시각·만료 시각만 반환한다")
  void listsOwnRetainedVideos() throws Exception {
    String first = retainedVideo(ownerId);
    String second = retainedVideo(ownerId);
    retainedVideo(UUID.randomUUID());

    JsonNode body =
        MediaApi.json(
            mvc.perform(asUser(retainedVideosRequest(), ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].videoId").value(contains(second, first)))
                .andExpect(jsonPath("$.page.totalElements").value(2)));

    List<String> fields = List.copyOf(MediaApi.fieldNames(body.at("/items/0")));
    assertThat(fields).containsExactlyInAnyOrder("videoId", "deletedAt", "retainUntil");
  }

  @Test
  @DisplayName(
      "[SPEC-018 R-14] [SPEC-018 R-18] 철회하면 같은 보존 키의 video.retention.revoked를 한 번 기록하고 목록에서 뺀다")
  void revokesOne() throws Exception {
    String videoId = retainedVideo(ownerId);
    String keepId = retainedVideo(ownerId);
    String retentionKey = payload("video.deleted", videoId).at("/retention/retentionKey").asText();

    mvc.perform(asUser(revokeRequest(videoId), ownerId)).andExpect(status().isNoContent());

    JsonNode revoked = payload("video.retention.revoked", videoId);
    assertThat(ContractSchemas.validateEvent("video.retention.revoked.schema.json", revoked))
        .isEmpty();
    assertThat(revoked.get("retentionKey").asText()).isEqualTo(retentionKey);
    assertThat(revoked.get("sourceId").asText()).isEqualTo(videoId);
    assertThat(revoked.get("ownerId").asText()).isEqualTo(ownerId.toString());
    assertThat(eventCount("video.retention.revoked")).isEqualTo(1);
    mvc.perform(asUser(retainedVideosRequest(), ownerId))
        .andExpect(jsonPath("$.items[*].videoId").value(contains(keepId)));
  }

  private JsonNode payload(String eventType, String videoId) throws Exception {
    return json.readTree(
        jdbc.queryForObject(
            "SELECT payload::text FROM outbox_events WHERE event_type = ? AND aggregate_id = ?",
            String.class,
            eventType,
            UUID.fromString(videoId)));
  }

  private int eventCount(String eventType) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM outbox_events WHERE event_type = ?", Integer.class, eventType);
    return count == null ? 0 : count;
  }

  /** 동의한 영상을 만들고 지운 뒤 영상 ID를 돌려준다. */
  private String retainedVideo(UUID owner) throws Exception {
    String videoId =
        MediaApi.json(
                mvc.perform(
                    asUser(
                        createVideoRequest(
                            "a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                        owner)))
            .at("/video/id")
            .asText();
    mvc.perform(asUser(deleteRequest(videoId), owner)).andExpect(status().isNoContent());
    return videoId;
  }
}
