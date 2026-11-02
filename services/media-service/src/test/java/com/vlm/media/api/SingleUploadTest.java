package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.createVideo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SingleUploadTest {

  @Autowired MockMvc mvc;

  @Test
  @DisplayName("[SPEC-003 R-2] 기준 크기 이하는 단일 업로드 주소를 주고, 저장소는 신고한 크기의 파일을 받는다")
  void storageAcceptsDeclaredSize() throws Exception {
    JsonNode upload = createVideo(mvc, UUID.randomUUID(), "a.mp4", 1000, "video/mp4").get("upload");

    assertThat(upload.get("method").asText()).isEqualTo("SINGLE");
    assertThat(StorageUploads.postForm(upload, new byte[1000]).is2xxSuccessful()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-003 R-2] 저장소는 신고한 크기와 다른 파일을 거절한다")
  void storageRejectsDifferentSize() throws Exception {
    JsonNode upload = createVideo(mvc, UUID.randomUUID(), "a.mp4", 1000, "video/mp4").get("upload");

    assertThat(StorageUploads.postForm(upload, new byte[1001]).is4xxClientError()).isTrue();
    assertThat(StorageUploads.postForm(upload, new byte[999]).is4xxClientError()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-003 NFR-6] 단일 업로드 주소는 1시간 동안 유효하다")
  void singleUploadExpiresInOneHour() throws Exception {
    Instant before = Instant.now();
    JsonNode upload = createVideo(mvc, UUID.randomUUID(), "a.mp4", 1000, "video/mp4").get("upload");

    Instant expiresAt = Instant.parse(upload.get("expiresAt").asText());
    assertThat(Duration.between(before, expiresAt))
        .isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
  }
}
