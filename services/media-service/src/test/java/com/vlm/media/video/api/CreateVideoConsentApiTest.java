package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.updateRetentionSettingsRequest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CreateVideoConsentApiTest {

  @Autowired MockMvc mvc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-018 R-3] [SPEC-018 R-5] 동의 값 없이 업로드 자리를 받으면 그 시점의 계정 기본값을 저장하고 응답에 담는다")
  void usesAccountDefault() throws Exception {
    mvc.perform(asUser(updateRetentionSettingsRequest(true), ownerId)).andExpect(status().isOk());

    String videoId =
        MediaApi.json(
                mvc.perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.video.retentionConsent").value(true)))
            .at("/video/id")
            .asText();

    mvc.perform(asUser(updateRetentionSettingsRequest(false), ownerId));
    mvc.perform(asUser(getRequest(videoId), ownerId))
        .andExpect(jsonPath("$.retentionConsent").value(true));
  }

  @Test
  @DisplayName("[SPEC-018 R-4] 업로드 자리 요청에 동의 값이 있으면 계정 기본값보다 그 값을 저장한다")
  void requestValueWins() throws Exception {
    mvc.perform(
            asUser(
                createVideoRequest("a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                ownerId))
        .andExpect(jsonPath("$.video.retentionConsent").value(true));

    mvc.perform(asUser(updateRetentionSettingsRequest(true), ownerId));
    mvc.perform(
            asUser(
                createVideoRequest("b.mp4", 1000, "video/mp4", Map.of("retentionConsent", false)),
                ownerId))
        .andExpect(jsonPath("$.video.retentionConsent").value(false));
  }
}
