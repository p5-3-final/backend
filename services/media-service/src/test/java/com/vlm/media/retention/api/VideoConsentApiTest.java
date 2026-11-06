package com.vlm.media.retention.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.consentRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.getRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class VideoConsentApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-018 R-6] 소유자가 영상 동의를 바꾸면 200과 영상 정보를 반환하고 이벤트는 내지 않는다")
  void changesConsent() throws Exception {
    String videoId = newVideo();

    mvc.perform(asUser(consentRequest(videoId, true), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(videoId))
        .andExpect(jsonPath("$.retentionConsent").value(true));
    mvc.perform(asUser(getRequest(videoId), ownerId))
        .andExpect(jsonPath("$.retentionConsent").value(true));
    mvc.perform(asUser(consentRequest(videoId, false), ownerId))
        .andExpect(jsonPath("$.retentionConsent").value(false));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
  }

  private String newVideo() throws Exception {
    return createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
  }
}
