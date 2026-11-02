package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class CreateVideoApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-003 R-1] 업로드 자리를 요청하면 요청자 소유의 업로드 중 영상을 만들고 201을 반환한다")
  void createsUploadingVideo() throws Exception {
    UUID ownerId = UUID.randomUUID();

    JsonNode body =
        json(
            mvc.perform(asUser(createVideoRequest("parking-lot.mp4", 1000, "video/mp4"), ownerId))
                .andExpect(status().isCreated())
                .andExpect(
                    header()
                        .string(
                            "Location", org.hamcrest.Matchers.startsWith("/api/v1/media/videos/")))
                .andExpect(jsonPath("$.video.status").value("UPLOADING"))
                .andExpect(jsonPath("$.video.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.video.fileName").value("parking-lot.mp4"))
                .andExpect(jsonPath("$.video.sizeBytes").value(1000))
                .andExpect(jsonPath("$.video.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.video.uploadedAt").isEmpty()));

    String id = body.get("video").get("id").asText();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM videos WHERE public_id = ?::uuid", String.class, id))
        .isEqualTo("UPLOADING");
  }

  @ParameterizedTest
  @CsvSource({"0", "-1", "5368709121"})
  @DisplayName("[SPEC-003 R-4] 파일 크기가 0 이하이거나 5GB를 넘으면 400 MEDIA_FILE_SIZE_INVALID를 반환한다")
  void rejectsInvalidSize(long sizeBytes) throws Exception {
    mvc.perform(asUser(createVideoRequest("a.mp4", sizeBytes, "video/mp4"), UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_FILE_SIZE_INVALID"));
  }

  @ParameterizedTest
  @CsvSource({"a.mp4,video/quicktime", "a.txt,text/plain", "a.mp4,text/plain", "noext,video/mp4"})
  @DisplayName("[SPEC-003 R-5] 확장자와 형식이 허용된 짝이 아니면 400 MEDIA_UNSUPPORTED_FORMAT을 반환한다")
  void rejectsUnsupportedFormat(String fileName, String contentType) throws Exception {
    mvc.perform(asUser(createVideoRequest(fileName, 1000, contentType), UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MEDIA_UNSUPPORTED_FORMAT"));
  }

  @Test
  @DisplayName("[SPEC-003 R-5] 확장자는 대소문자를 구분하지 않는다")
  void acceptsUpperCaseExtension() throws Exception {
    mvc.perform(asUser(createVideoRequest("CLIP.MOV", 1000, "video/quicktime"), UUID.randomUUID()))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-003 R-6] 저장 경로는 영상 ID로만 만들고 파일 이름을 쓰지 않는다")
  void storageKeyUsesVideoIdOnly() throws Exception {
    JsonNode body =
        json(
            mvc.perform(
                    asUser(
                        createVideoRequest("../../etc/passwd.mp4", 1000, "video/mp4"),
                        UUID.randomUUID()))
                .andExpect(status().isCreated()));

    String id = body.get("video").get("id").asText();
    String storageKey =
        jdbc.queryForObject(
            "SELECT storage_key FROM videos WHERE public_id = ?::uuid", String.class, id);
    assertThat(storageKey).isEqualTo("sources/" + id + "/original");
    assertThat(body.get("upload").get("fields").get("key").asText()).isEqualTo(storageKey);
  }

  @Test
  @DisplayName("사용자 헤더가 없으면 401 AUTH_TOKEN_MISSING을 반환한다")
  void rejectsMissingUserHeader() throws Exception {
    mvc.perform(createVideoRequest("a.mp4", 1000, "video/mp4"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
  }
}
