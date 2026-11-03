package com.vlm.media.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VideoTest {

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");

  @Test
  @DisplayName("[SPEC-003 R-12] 만료된 영상은 업로드 완료로 바꿀 수 없다")
  void expiredVideoCannotComplete() {
    Video video = newVideo();
    video.expire();

    assertThatThrownBy(() -> video.completeUpload(NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MediaErrorCode.INVALID_STATE));
    assertThat(video.getStatus()).isEqualTo(VideoStatus.EXPIRED);
  }

  @Test
  @DisplayName("[SPEC-003 R-12] 삭제된 영상은 업로드 완료로 바꿀 수 없다")
  void deletedVideoCannotComplete() {
    Video video = newVideo();
    video.delete(NOW);

    assertThatThrownBy(() -> video.completeUpload(NOW))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(MediaErrorCode.INVALID_STATE));
    assertThat(video.getStatus()).isEqualTo(VideoStatus.DELETED);
  }

  @Test
  @DisplayName("[SPEC-003 R-13] 업로드 중인 영상은 만료로 바뀐다")
  void uploadingVideoExpires() {
    Video video = newVideo();

    video.expire();

    assertThat(video.getStatus()).isEqualTo(VideoStatus.EXPIRED);
  }

  @Test
  @DisplayName("[SPEC-003 R-13] 업로드가 끝난 영상은 만료되지 않는다")
  void uploadedVideoDoesNotExpire() {
    Video video = newVideo();
    video.completeUpload(NOW);

    video.expire();

    assertThat(video.getStatus()).isEqualTo(VideoStatus.UPLOADED);
  }

  private static final NormalizationResult RESULT =
      new NormalizationResult(
          UUID.fromString("0192f0d4-0000-7000-8000-000000000001"),
          "normalize-v1",
          "sources/x/normalizations/n/normalized.mp4",
          "sources/x/normalizations/n/hls/index.m3u8",
          612_480);

  @Test
  @DisplayName("[SPEC-007 R-15] 업로드 완료된 영상은 정규화 시작 신호로 정규화 중이 된다")
  void uploadedVideoStartsNormalizing() {
    Video video = uploadedVideo();

    assertThat(video.startNormalizing()).isTrue();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.NORMALIZING);
  }

  @Test
  @DisplayName("[SPEC-007 R-16] 정규화 중인 영상은 성공 결과로 준비됨이 되고 결과를 남긴다")
  void normalizingVideoBecomesReady() {
    Video video = uploadedVideo();
    video.startNormalizing();

    assertThat(video.completeNormalization(RESULT, 613, NOW)).isTrue();

    assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
    assertThat(video.getNormalizationId()).isEqualTo(RESULT.normalizationId());
    assertThat(video.getNormalizationVersion()).isEqualTo("normalize-v1");
    assertThat(video.getNormalizedKey()).isEqualTo(RESULT.normalizedKey());
    assertThat(video.getHlsPlaylistKey()).isEqualTo(RESULT.hlsPlaylistKey());
    assertThat(video.getDurationMs()).isEqualTo(612_480L);
    assertThat(video.getEstimatedProcessingSec()).isEqualTo(613);
    assertThat(video.getRejectReason()).isNull();
    assertThat(video.getNormalizedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("[SPEC-007 R-16] 시작 신호보다 결과가 먼저 와도 업로드 완료에서 바로 준비됨이 된다")
  void uploadedVideoBecomesReadyWithoutStartSignal() {
    Video video = uploadedVideo();

    assertThat(video.completeNormalization(RESULT, 613, NOW)).isTrue();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
  }

  @Test
  @DisplayName("[SPEC-007 R-17] 정규화 중인 영상은 거절 결과로 거절됨이 되고 사유를 남긴다")
  void normalizingVideoIsRejected() {
    Video video = uploadedVideo();
    video.startNormalizing();

    assertThat(
            video.rejectNormalization(
                RESULT.normalizationId(), "normalize-v1", RejectReason.TOO_LONG, NOW))
        .isTrue();

    assertThat(video.getStatus()).isEqualTo(VideoStatus.REJECTED);
    assertThat(video.getRejectReason()).isEqualTo(RejectReason.TOO_LONG);
    assertThat(video.getNormalizationId()).isEqualTo(RESULT.normalizationId());
    assertThat(video.getDurationMs()).isNull();
    assertThat(video.getNormalizedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("[SPEC-007 R-18] 늦게 온 정규화 시작 신호는 준비됨 상태를 되돌리지 않는다")
  void lateStartSignalIsIgnored() {
    Video video = uploadedVideo();
    video.completeNormalization(RESULT, 613, NOW);

    assertThat(video.startNormalizing()).isFalse();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
  }

  @Test
  @DisplayName("[SPEC-007 R-18] 이미 결과가 반영된 영상은 다른 결과로 바뀌지 않는다")
  void secondResultIsIgnored() {
    Video video = uploadedVideo();
    video.completeNormalization(RESULT, 613, NOW);

    assertThat(
            video.rejectNormalization(
                UUID.randomUUID(), "normalize-v2", RejectReason.DECODE_FAILED, NOW))
        .isFalse();
    assertThat(video.getStatus()).isEqualTo(VideoStatus.READY);
    assertThat(video.getRejectReason()).isNull();
  }

  @Test
  @DisplayName("[SPEC-007 R-18] 업로드 중·만료·삭제된 영상은 정규화 신호로 바뀌지 않는다")
  void otherStatesIgnoreNormalization() {
    Video uploading = newVideo();
    Video deleted = uploadedVideo();
    deleted.delete(NOW);

    assertThat(uploading.startNormalizing()).isFalse();
    assertThat(uploading.completeNormalization(RESULT, 1, NOW)).isFalse();
    assertThat(deleted.completeNormalization(RESULT, 1, NOW)).isFalse();
    assertThat(uploading.getStatus()).isEqualTo(VideoStatus.UPLOADING);
    assertThat(deleted.getStatus()).isEqualTo(VideoStatus.DELETED);
  }

  @Test
  @DisplayName("[SPEC-007 R-19] 삭제된 영상은 저장소 정리를 다시 요청할 수 있다")
  void deletedVideoRequestsCleanupAgain() {
    Video video = uploadedVideo();
    video.delete(NOW);
    video.markStorageCleaned(NOW);

    assertThat(video.requestStorageCleanupAgain()).isTrue();
    assertThat(video.getStorageCleanedAt()).isNull();
  }

  @Test
  @DisplayName("[SPEC-007 R-19] 삭제되지 않은 영상은 저장소 정리를 요청하지 않는다")
  void liveVideoDoesNotRequestCleanup() {
    assertThat(uploadedVideo().requestStorageCleanupAgain()).isFalse();
  }

  private static Video uploadedVideo() {
    Video video = newVideo();
    video.completeUpload(NOW);
    return video;
  }

  private static Video newVideo() {
    return Video.startUpload(
        UUID.randomUUID(), "a.mp4", 1000, "video/mp4", NOW, Duration.ofHours(24));
  }
}
