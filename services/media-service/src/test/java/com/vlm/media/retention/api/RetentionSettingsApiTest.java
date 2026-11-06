package com.vlm.media.retention.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.retentionSettingsRequest;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.media.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RetentionSettingsApiTest {

  @Autowired MockMvc mvc;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-018 R-1] 기본값을 바꾼 적 없으면 보존 설정의 기본 동의는 false다")
  void defaultIsFalse() throws Exception {
    mvc.perform(asUser(retentionSettingsRequest(), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultConsent").value(false));
  }
}
