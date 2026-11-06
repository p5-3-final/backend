package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createSession;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CreateVideoSessionApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-017 R-6] [SPEC-017 R-13] 세션과 역할로 업로드 자리를 받으면 영상이 세션에 들어가고 응답에 담긴다")
  void putsVideoIntoSession() throws Exception {
    String sessionId = createSession(mvc, ownerId, "방호벽 교체");

    String videoId =
        MediaApi.json(
                mvc.perform(
                        asUser(
                            createVideoRequest(
                                "before.mp4",
                                1000,
                                "video/mp4",
                                Map.of("sessionId", sessionId, "role", "BEFORE")),
                            ownerId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.video.session.sessionId").value(sessionId))
                    .andExpect(jsonPath("$.video.session.role").value("BEFORE")))
            .at("/video/id")
            .asText();

    mvc.perform(asUser(getRequest(videoId), ownerId))
        .andExpect(jsonPath("$.session.sessionId").value(sessionId))
        .andExpect(jsonPath("$.session.role").value("BEFORE"));
  }

  @Test
  @DisplayName("[SPEC-017 R-13] 세션 없이 올린 영상의 응답은 session이 null이다")
  void videoWithoutSessionHasNullSession() throws Exception {
    mvc.perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.video.session").isEmpty());
  }

  @Test
  @DisplayName("[SPEC-017 R-10] 삭제가 아닌 영상이 100개인 세션이면 409를 반환하고 영상을 만들지 않는다")
  void rejectsFullSession() throws Exception {
    String sessionId = createSession(mvc, ownerId, "가득 찬 세션");
    fillSession(sessionId, 100);

    mvc.perform(
            asUser(
                createVideoRequest(
                    "a.mp4", 1000, "video/mp4", Map.of("sessionId", sessionId, "role", "AFTER")),
                ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_SESSION_FULL"));
    assertThat(videoCount()).isEqualTo(100);
  }

  @Test
  @DisplayName("[SPEC-017 R-10] 삭제한 영상은 세션의 영상 수에 세지 않는다")
  void deletedVideosDoNotCount() throws Exception {
    String sessionId = createSession(mvc, ownerId, "세션");
    fillSession(sessionId, 100);
    jdbc.update("UPDATE videos SET status = 'DELETED' WHERE id = (SELECT min(id) FROM videos)");

    mvc.perform(
            asUser(
                createVideoRequest(
                    "a.mp4", 1000, "video/mp4", Map.of("sessionId", sessionId, "role", "AFTER")),
                ownerId))
        .andExpect(status().isCreated());
  }

  private void fillSession(String sessionId, int count) {
    jdbc.update(
        """
        INSERT INTO videos (public_id, owner_id, file_name, size_bytes, content_type, status,
            storage_key, expires_at, session_id, session_role)
        SELECT gen_random_uuid(), ?, 'v.mp4', 1, 'video/mp4', 'UPLOADED', 'k', now(), ?, 'DURING'
        FROM generate_series(1, ?)
        """,
        ownerId,
        UUID.fromString(sessionId),
        count);
  }

  private long videoCount() {
    Long count = jdbc.queryForObject("SELECT count(*) FROM videos", Long.class);
    return count == null ? 0 : count;
  }
}
