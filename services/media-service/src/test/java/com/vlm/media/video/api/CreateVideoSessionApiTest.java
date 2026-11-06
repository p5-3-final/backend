package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createSession;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.getRequest;
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
}
