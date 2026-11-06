package com.vlm.media.session.api;

import static com.vlm.media.support.MediaApi.asAdmin;
import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createSession;
import static com.vlm.media.support.MediaApi.createSessionRequest;
import static com.vlm.media.support.MediaApi.createSessionVideo;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.sessionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class WorkSessionApiTest {

  @Autowired MockMvc mvc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-017 R-1] 이름·현장·구역으로 세션을 만들면 201과 요청자 소유 세션을 반환한다")
  void createsSession() throws Exception {
    JsonNode session =
        MediaApi.json(
            mvc.perform(
                    asUser(
                        createSessionRequest(
                            Map.of("name", "방호벽 교체", "site", "평택 공장", "area", "3번 라인")),
                        ownerId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.name").value("방호벽 교체"))
                .andExpect(jsonPath("$.site").value("평택 공장"))
                .andExpect(jsonPath("$.area").value("3번 라인"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()));

    assertThat(UUID.fromString(session.get("id").asText())).isNotNull();
    mvc.perform(asUser(createSessionRequest(Map.of("name", "이름만")), ownerId))
        .andExpect(status().isCreated())
        .andExpect(
            header()
                .string("Location", org.hamcrest.Matchers.startsWith("/api/v1/media/sessions/")))
        .andExpect(jsonPath("$.site").isEmpty())
        .andExpect(jsonPath("$.area").isEmpty());
  }

  @ParameterizedTest
  @MethodSource("invalidSessions")
  @DisplayName("[SPEC-017 R-2] 이름이 없거나 범위를 벗어나거나 현장·구역이 100자를 넘으면 400을 반환한다")
  void rejectsInvalidSession(Map<String, String> body) throws Exception {
    mvc.perform(asUser(createSessionRequest(body), ownerId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_REQUEST"));
  }

  static Stream<Map<String, String>> invalidSessions() {
    String long101 = "가".repeat(101);
    return Stream.of(
        Map.of("site", "평택"),
        Map.of("name", ""),
        Map.of("name", " "),
        Map.of("name", long101),
        Map.of("name", "a", "site", long101),
        Map.of("name", "a", "area", long101));
  }

  @Test
  @DisplayName("[SPEC-017 R-4] 세션 상세는 삭제가 아닌 영상을 역할 순서로 반환하고 ADMIN도 볼 수 있다")
  void detailListsVideosInOrder() throws Exception {
    String sessionId = createSession(mvc, ownerId, "방호벽 교체");
    String after = createSessionVideo(mvc, ownerId, sessionId, "AFTER");
    String before = createSessionVideo(mvc, ownerId, sessionId, "BEFORE");
    String deleted = createSessionVideo(mvc, ownerId, sessionId, "DURING");
    mvc.perform(asUser(deleteRequest(deleted), ownerId)).andExpect(status().isNoContent());

    mvc.perform(asUser(sessionRequest(sessionId), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(sessionId))
        .andExpect(jsonPath("$.name").value("방호벽 교체"))
        .andExpect(jsonPath("$.videos[*].id").value(contains(before, after)))
        .andExpect(jsonPath("$.videos[0].session.role").value("BEFORE"));
    mvc.perform(asAdmin(sessionRequest(sessionId), UUID.randomUUID()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.videos.length()").value(2));
  }
}
