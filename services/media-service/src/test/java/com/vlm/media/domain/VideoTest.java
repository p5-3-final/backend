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

  private static Video newVideo() {
    return Video.startUpload(
        UUID.randomUUID(), "a.mp4", 1000, "video/mp4", NOW, Duration.ofHours(24));
  }
}
