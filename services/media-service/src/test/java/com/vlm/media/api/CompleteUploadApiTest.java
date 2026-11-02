package com.vlm.media.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StoragePuts;
import com.vlm.media.support.StorageUploads;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class CompleteUploadApiTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-7] 신고한 크기의 파일이 올라가 있으면 업로드 완료로 바꾸고 200을 반환한다")
  void completesUploadedFile() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);

    mvc.perform(asUser(completeRequest(id(created)), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"))
        .andExpect(jsonPath("$.uploadedAt").isNotEmpty());
  }

  @Test
  @DisplayName("[SPEC-003 R-10] 파일이 없으면 409 MEDIA_UPLOAD_INCOMPLETE를 반환하고 상태를 바꾸지 않는다")
  void rejectsMissingFile() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    mvc.perform(asUser(completeRequest(id(created)), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_INCOMPLETE"));
    assertThat(statusOf(created)).isEqualTo("UPLOADING");
  }

  @Test
  @DisplayName("[SPEC-003 R-10] 파일 크기가 신고한 크기와 다르면 409 MEDIA_UPLOAD_INCOMPLETE를 반환한다")
  void rejectsDifferentSize() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StoragePuts.put(s3, "sources/" + id(created) + "/original", new byte[999]);

    mvc.perform(asUser(completeRequest(id(created)), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_INCOMPLETE"));
    assertThat(statusOf(created)).isEqualTo("UPLOADING");
  }

  @Test
  @DisplayName("[SPEC-003 R-11] 이미 완료된 영상에 다시 요청하면 상태를 바꾸지 않고 200을 반환한다")
  void completingTwiceIsIdempotent() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    String first =
        mvc.perform(asUser(completeRequest(id(created)), ownerId))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String second =
        mvc.perform(asUser(completeRequest(id(created)), ownerId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(second).isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid",
                Integer.class,
                id(created)))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 소유자가 아니면 완료 요청은 404 MEDIA_VIDEO_NOT_FOUND다")
  void rejectsNonOwner() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);

    mvc.perform(asUser(completeRequest(id(created)), UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_VIDEO_NOT_FOUND"));
    mvc.perform(asUser(completeRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound());
  }

  @ParameterizedTest
  @ValueSource(strings = {"EXPIRED", "DELETED"})
  @DisplayName("[SPEC-003 R-12] 만료·삭제된 영상에 완료 요청이 오면 409 MEDIA_INVALID_STATE를 반환한다")
  void rejectsExpiredOrDeleted(String state) throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    jdbc.update("UPDATE videos SET status = ? WHERE public_id = ?::uuid", state, id(created));

    mvc.perform(asUser(completeRequest(id(created)), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_INVALID_STATE"));
    assertThat(statusOf(created)).isEqualTo(state);
  }

  private String statusOf(JsonNode created) {
    return jdbc.queryForObject(
        "SELECT status FROM videos WHERE public_id = ?::uuid", String.class, id(created));
  }

  private static String id(JsonNode created) {
    return created.get("video").get("id").asText();
  }
}
