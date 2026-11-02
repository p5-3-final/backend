package com.vlm.media.application;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.TestcontainersConfiguration;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

// 테스트 설정: 단일 업로드 기준 1MiB, 조각 5MiB (IntegrationTest)
@IntegrationTest
class UploadExpiryTest {

  private static final int PART = 5 * 1024 * 1024;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;
  @Autowired UploadExpiryJob expiryJob;
  @Autowired StorageCleanupJob cleanupJob;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-13] 업로드 중으로 24시간이 지난 영상만 만료로 바꾼다")
  void expiresOnlyOverdueUploads() throws Exception {
    String overdue = newVideo();
    String fresh = newVideo();
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    String uploaded = created.at("/video/id").asText();
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    mvc.perform(asUser(completeRequest(uploaded), ownerId));
    pastExpiry(overdue);
    pastExpiry(uploaded);

    expiryJob.expire();

    assertThat(statusOf(overdue)).isEqualTo("EXPIRED");
    assertThat(statusOf(fresh)).isEqualTo("UPLOADING");
    assertThat(statusOf(uploaded)).isEqualTo("UPLOADED");
  }

  @Test
  @DisplayName("[SPEC-003 R-13] 만료된 영상의 올라간 파일을 지운다")
  void deletesFilesOfExpiredVideo() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    String videoId = created.at("/video/id").asText();
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    pastExpiry(videoId);

    expiryJob.expire();
    cleanupJob.cleanUp();

    assertThat(
            s3.listObjectsV2(
                    r ->
                        r.bucket(TestcontainersConfiguration.BUCKET)
                            .prefix("sources/" + videoId + "/"))
                .contents())
        .isEmpty();
  }

  @Test
  @DisplayName("[SPEC-003 R-13] 만료된 영상의 끝나지 않은 조각을 지운다")
  void abortsPartsOfExpiredVideo() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", PART + 1000, "video/mp4");
    String videoId = created.at("/video/id").asText();
    StorageUploads.putParts(created.get("upload"), new byte[PART + 1000]);
    pastExpiry(videoId);

    expiryJob.expire();
    cleanupJob.cleanUp();

    assertThat(
            s3.listMultipartUploads(
                    r ->
                        r.bucket(TestcontainersConfiguration.BUCKET)
                            .prefix("sources/" + videoId + "/"))
                .uploads())
        .isEmpty();
  }

  private String newVideo() throws Exception {
    return createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4").at("/video/id").asText();
  }

  private void pastExpiry(String videoId) {
    jdbc.update(
        "UPDATE videos SET expires_at = now() - interval '1 second' WHERE public_id = ?::uuid",
        videoId);
  }

  private String statusOf(String videoId) {
    return jdbc.queryForObject(
        "SELECT status FROM videos WHERE public_id = ?::uuid", String.class, videoId);
  }
}
