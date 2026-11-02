package com.vlm.media.application;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.TestcontainersConfiguration;
import com.vlm.media.domain.UploadStorage;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;

// 테스트 설정: 단일 업로드 기준 1MiB, 조각 5MiB (IntegrationTest)
@IntegrationTest
class StorageCleanupTest {

  private static final int PART = 5 * 1024 * 1024;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;
  @Autowired StorageCleanupJob cleanupJob;
  @MockitoSpyBean UploadStorage uploadStorage;

  private final UUID ownerId = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-003 R-20] 삭제된 영상의 파일을 지우고, 삭제되지 않은 영상의 파일은 남긴다")
  void deletesFilesOfDeletedVideo() throws Exception {
    String deleted = uploadedVideo();
    String kept = uploadedVideo();
    mvc.perform(asUser(deleteRequest(deleted), ownerId));

    cleanupJob.cleanUp();

    assertThat(objectKeys(deleted)).isEmpty();
    assertThat(objectKeys(kept)).containsExactly("sources/" + kept + "/original");
    assertThat(cleanedAt(deleted)).isNotNull();
    assertThat(cleanedAt(kept)).isNull();
  }

  @Test
  @DisplayName("[SPEC-003 R-20] 삭제된 영상의 끝나지 않은 멀티파트 조각을 지운다")
  void abortsUnfinishedMultipartUpload() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", PART + 1000, "video/mp4");
    String videoId = created.at("/video/id").asText();
    StorageUploads.putParts(created.get("upload"), new byte[PART + 1000]);
    mvc.perform(asUser(deleteRequest(videoId), ownerId));

    cleanupJob.cleanUp();

    assertThat(unfinishedUploads(videoId)).isEmpty();
    assertThat(cleanedAt(videoId)).isNotNull();
  }

  @Test
  @DisplayName("[SPEC-003 R-20] 멀티파트로 완료한 뒤 삭제한 영상도 파일을 지운다")
  void deletesCompletedMultipartVideo() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", PART + 1000, "video/mp4");
    String videoId = created.at("/video/id").asText();
    List<Map<String, Object>> parts =
        StorageUploads.putParts(created.get("upload"), new byte[PART + 1000]);
    mvc.perform(asUser(completeRequest(videoId, parts), ownerId));
    mvc.perform(asUser(deleteRequest(videoId), ownerId));

    cleanupJob.cleanUp();

    assertThat(objectKeys(videoId)).isEmpty();
    assertThat(cleanedAt(videoId)).isNotNull();
  }

  @Test
  @DisplayName("[SPEC-003 R-20] 저장소 정리에 실패하면 다음 주기에 다시 시도한다")
  void retriesFailedCleanup() throws Exception {
    String videoId = uploadedVideo();
    mvc.perform(asUser(deleteRequest(videoId), ownerId));
    doReturn(false).when(uploadStorage).deleteVideoFiles(any(), any(), any());

    cleanupJob.cleanUp();
    Object afterFailure = cleanedAt(videoId);
    reset(uploadStorage);
    cleanupJob.cleanUp();

    assertThat(afterFailure).isNull();
    assertThat(objectKeys(videoId)).isEmpty();
    assertThat(cleanedAt(videoId)).isNotNull();
  }

  private String uploadedVideo() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    String videoId = created.at("/video/id").asText();
    mvc.perform(asUser(completeRequest(videoId), ownerId));
    return videoId;
  }

  private List<String> objectKeys(String videoId) {
    return s3
        .listObjectsV2(
            r -> r.bucket(TestcontainersConfiguration.BUCKET).prefix("sources/" + videoId + "/"))
        .contents()
        .stream()
        .map(o -> o.key())
        .toList();
  }

  private List<String> unfinishedUploads(String videoId) {
    return s3
        .listMultipartUploads(
            r -> r.bucket(TestcontainersConfiguration.BUCKET).prefix("sources/" + videoId + "/"))
        .uploads()
        .stream()
        .map(u -> u.uploadId())
        .toList();
  }

  private Object cleanedAt(String videoId) {
    return jdbc.queryForMap(
            "SELECT storage_cleaned_at FROM videos WHERE public_id = ?::uuid", videoId)
        .get("storage_cleaned_at");
  }
}
