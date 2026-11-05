package com.vlm.media.normalization.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.media.TestcontainersConfiguration;
import com.vlm.media.shared.domain.NormalizationResult;
import com.vlm.media.shared.domain.RejectReason;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StoragePuts;
import com.vlm.media.video.application.StorageCleanupJob;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class NormalizationEventServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Autowired NormalizationEventService service;
  @Autowired VideoRepository videos;
  @Autowired StorageCleanupJob cleanupJob;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;

  @Test
  @DisplayName("[SPEC-007 R-15] 정규화 시작 신호를 받으면 업로드 완료 영상이 정규화 중이 된다")
  void startSignalMovesToNormalizing() {
    UUID id = uploadedVideo();

    service.onNormalizing(id);

    assertThat(row(id)).containsEntry("status", "NORMALIZING");
  }

  @Test
  @DisplayName("[SPEC-007 R-16, R-20] 성공 결과를 받으면 준비됨이 되고 결과와 예상 처리 시간을 저장한다")
  void readyOutcomeIsStored() {
    UUID id = uploadedVideo();
    service.onNormalizing(id);
    NormalizationResult result = result(id);

    service.onNormalized(new NormalizationOutcome.Ready(id, result));

    assertThat(row(id))
        .containsEntry("status", "READY")
        .containsEntry("normalization_id", result.normalizationId())
        .containsEntry("normalization_version", "normalize-v1")
        .containsEntry("normalized_key", result.normalizedKey())
        .containsEntry("hls_playlist_key", result.hlsPlaylistKey())
        .containsEntry("duration_ms", 612_480L)
        // 기본 처리 계수 1.0
        .containsEntry("estimated_processing_sec", 613)
        .containsEntry("reject_reason", null);
    assertThat(row(id).get("normalized_at")).isNotNull();
  }

  @Test
  @DisplayName("[SPEC-007 R-17] 거절 결과를 받으면 거절됨이 되고 사유를 저장한다")
  void rejectedOutcomeIsStored() {
    UUID id = uploadedVideo();
    UUID normalization = UUID.randomUUID();

    service.onNormalized(
        new NormalizationOutcome.Rejected(
            id, normalization, "normalize-v1", RejectReason.NO_VIDEO_STREAM));

    assertThat(row(id))
        .containsEntry("status", "REJECTED")
        .containsEntry("reject_reason", "NO_VIDEO_STREAM")
        .containsEntry("normalization_id", normalization)
        .containsEntry("duration_ms", null);
  }

  @Test
  @DisplayName("[SPEC-007 R-18] 이미 준비됨인 영상은 늦게 온 시작 신호와 다른 결과로 바뀌지 않는다")
  void settledVideoIgnoresLateEvents() {
    UUID id = uploadedVideo();
    service.onNormalized(new NormalizationOutcome.Ready(id, result(id)));

    service.onNormalizing(id);
    service.onNormalized(
        new NormalizationOutcome.Rejected(
            id, UUID.randomUUID(), "normalize-v2", RejectReason.DECODE_FAILED));

    assertThat(row(id)).containsEntry("status", "READY").containsEntry("reject_reason", null);
  }

  @Test
  @DisplayName("[SPEC-007 R-19] 삭제된 영상에 결과가 오면 저장소 정리를 다시 해서 새 정규화 파일을 지운다")
  void deletedVideoResultTriggersCleanupAgain() {
    UUID id = uploadedVideo();
    Video video = videos.findByPublicId(id).orElseThrow();
    video.delete(NOW);
    video.markStorageCleaned(NOW);
    videos.save(video);
    NormalizationResult result = result(id);
    // 삭제 뒤에 끝난 정규화가 남긴 파일
    StoragePuts.put(s3, result.normalizedKey(), new byte[] {1, 2, 3});

    service.onNormalized(new NormalizationOutcome.Ready(id, result));
    cleanupJob.cleanUp();

    assertThat(row(id)).containsEntry("status", "DELETED");
    assertThat(row(id).get("storage_cleaned_at")).isNotNull();
    assertThat(objectExists(result.normalizedKey())).isFalse();
  }

  @Test
  @DisplayName("[SPEC-007] 모르는 영상의 이벤트는 아무것도 바꾸지 않고 끝난다")
  void unknownVideoIsIgnored() {
    UUID unknown = UUID.randomUUID();

    service.onNormalizing(unknown);
    service.onNormalized(new NormalizationOutcome.Ready(unknown, result(unknown)));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isZero();
  }

  private UUID uploadedVideo() {
    Video video =
        Video.startUpload(UUID.randomUUID(), "a.mp4", 1000, "video/mp4", NOW, Duration.ofHours(24));
    video.completeUpload(NOW);
    return videos.save(video).getPublicId();
  }

  private static NormalizationResult result(UUID sourceId) {
    UUID normalization = UUID.randomUUID();
    String base = "sources/" + sourceId + "/normalizations/" + normalization + "/";
    return new NormalizationResult(
        normalization, "normalize-v1", base + "normalized.mp4", base + "hls/index.m3u8", 612_480);
  }

  private Map<String, Object> row(UUID id) {
    return jdbc.queryForMap("SELECT * FROM videos WHERE public_id = ?", id);
  }

  private boolean objectExists(String key) {
    return s3.listObjectsV2(r -> r.bucket(TestcontainersConfiguration.BUCKET).prefix(key))
        .hasContents();
  }
}
