package com.vlm.media.session.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createSessionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
