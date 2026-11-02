package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.getRequest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GetVideoApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-15] 소유자에게 ID·파일 이름·크기·형식·상태·시각을 반환한다")
  void returnsDetails() throws Exception {
    JsonNode video = createVideo(mvc, ownerId, "parking.mp4", 1000, "video/mp4").get("video");

    mvc.perform(asUser(getRequest(video.get("id").asText()), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(video.get("id").asText()))
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
        .andExpect(jsonPath("$.fileName").value("parking.mp4"))
        .andExpect(jsonPath("$.sizeBytes").value(1000))
        .andExpect(jsonPath("$.contentType").value("video/mp4"))
        .andExpect(jsonPath("$.status").value("UPLOADING"))
        .andExpect(jsonPath("$.createdAt").value(video.get("createdAt").asText()))
        .andExpect(jsonPath("$.uploadedAt").isEmpty());
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 없는 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void missingVideoIsNotFound() throws Exception {
    mvc.perform(asUser(getRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 삭제된 영상이면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void deletedVideoIsNotFound() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
    jdbc.update("UPDATE videos SET status = 'DELETED' WHERE public_id = ?::uuid", id);

    mvc.perform(asUser(getRequest(id), ownerId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 소유자도 ADMIN도 아니면 404 MEDIA_VIDEO_NOT_FOUND를 반환한다")
  void otherUsersVideoIsNotFound() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();

    mvc.perform(asUser(getRequest(id), UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-003 R-17] ADMIN은 다른 사용자의 영상도 상세 조회할 수 있다")
  void adminCanViewOthersVideo() throws Exception {
    String id = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();

    mvc.perform(asAdmin(getRequest(id), UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ownerId").value(ownerId.toString()));
  }
}
