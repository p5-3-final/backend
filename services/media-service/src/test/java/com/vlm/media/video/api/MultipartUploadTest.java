package com.vlm.media.video.api;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.TestcontainersConfiguration;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

// 테스트 설정: 단일 업로드 기준 1MiB, 조각 5MiB (IntegrationTest)
@IntegrationTest
class MultipartUploadTest {

  private static final int PART = 5 * 1024 * 1024;
  private static final int SIZE = PART + 1000;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-3] 기준 크기를 넘으면 멀티파트를 시작하고 조각 크기와 조각별 주소를 준다")
  void startsMultipartUpload() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", SIZE, "video/mp4");

    JsonNode upload = created.get("upload");
    assertThat(upload.get("method").asText()).isEqualTo("MULTIPART");
    assertThat(upload.get("partSizeBytes").asLong()).isEqualTo(PART);
    assertThat(upload.get("parts")).hasSize(2);
    assertThat(upload.get("parts").get(0).get("partNumber").asInt()).isEqualTo(1);
    assertThat(upload.get("parts").get(1).get("partNumber").asInt()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT multipart_upload_id FROM videos WHERE public_id = ?::uuid",
                String.class,
                created.get("video").get("id").asText()))
        .isNotBlank();
  }

  @Test
  @DisplayName("[SPEC-003 NFR-6] 조각 주소는 1시간 동안 유효하다")
  void partUrlsExpireInOneHour() throws Exception {
    Instant before = Instant.now();
    JsonNode upload = createVideo(mvc, ownerId, "big.mp4", SIZE, "video/mp4").get("upload");

    assertThat(Duration.between(before, Instant.parse(upload.get("expiresAt").asText())))
        .isBetween(Duration.ofMinutes(59), Duration.ofMinutes(61));
    assertThat(upload.get("parts").get(0).get("url").asText()).contains("X-Amz-Expires=3600");
  }

  @Test
  @DisplayName("[SPEC-003 R-8] 조각 목록으로 완료 요청하면 하나의 파일로 합치고 업로드 완료로 바꾼다")
  void completesMultipartUpload() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", SIZE, "video/mp4");
    String videoId = created.get("video").get("id").asText();
    List<Map<String, Object>> parts =
        StorageUploads.putParts(created.get("upload"), new byte[SIZE]);

    mvc.perform(asUser(completeRequest(videoId, parts), ownerId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UPLOADED"));
    long stored =
        s3.headObject(
                r ->
                    r.bucket(TestcontainersConfiguration.BUCKET)
                        .key("sources/" + videoId + "/original"))
            .contentLength();
    assertThat(stored).isEqualTo(SIZE);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?::uuid",
                Integer.class,
                videoId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-003 R-8] 멀티파트 영상을 조각 목록 없이 완료 요청하면 409 MEDIA_UPLOAD_INCOMPLETE다")
  void rejectsMultipartWithoutParts() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", SIZE, "video/mp4");
    StorageUploads.putParts(created.get("upload"), new byte[SIZE]);

    mvc.perform(asUser(completeRequest(created.get("video").get("id").asText()), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_INCOMPLETE"));
  }

  @Test
  @DisplayName("[SPEC-003 R-8] 올라가지 않은 조각이 있으면 409 MEDIA_UPLOAD_INCOMPLETE다")
  void rejectsMissingPart() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", SIZE, "video/mp4");
    List<Map<String, Object>> parts =
        StorageUploads.putParts(created.get("upload"), new byte[SIZE]);
    List<Map<String, Object>> withWrongETag =
        List.of(parts.get(0), Map.of("partNumber", 2, "eTag", "\"0000\""));

    mvc.perform(
            asUser(
                completeRequest(created.get("video").get("id").asText(), withWrongETag), ownerId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_INCOMPLETE"));
  }

  @Test
  @DisplayName("[SPEC-003 R-4] 크기 5GB 정확히는 받는다")
  void acceptsExactlyFiveGb() throws Exception {
    mvc.perform(
            asUser(
                createVideoRequest("big.mkv", 5L * 1024 * 1024 * 1024, "video/x-matroska"),
                UUID.randomUUID()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.upload.method").value("MULTIPART"));
  }
}
