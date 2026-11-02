package com.vlm.media.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VideoFilePolicyTest {

  @ParameterizedTest
  @CsvSource({
    "a.mp4,video/mp4",
    "a.mov,video/quicktime",
    "a.mkv,video/x-matroska",
    "a.avi,video/x-msvideo",
    "A.MP4,video/mp4",
    "my.clip.v2.mkv,video/x-matroska"
  })
  @DisplayName("[SPEC-003 R-5] 허용한 확장자와 형식의 짝은 받는다")
  void acceptsAllowedPairs(String fileName, String contentType) {
    assertThatCode(() -> VideoFilePolicy.check(fileName, 1, contentType))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"a.mp4,video/x-msvideo", "a.webm,video/webm", "a,video/mp4", "a.mp4.exe,video/mp4"})
  @DisplayName("[SPEC-003 R-5] 짝이 맞지 않거나 허용하지 않은 형식이면 MEDIA_UNSUPPORTED_FORMAT")
  void rejectsOtherPairs(String fileName, String contentType) {
    assertThatThrownBy(() -> VideoFilePolicy.check(fileName, 1, contentType))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                    .isEqualTo(MediaErrorCode.UNSUPPORTED_FORMAT));
  }

  @ParameterizedTest
  @CsvSource({"1", "5368709120"})
  @DisplayName("[SPEC-003 R-4] 1바이트부터 5GB까지 받는다")
  void acceptsSizeRange(long sizeBytes) {
    assertThatCode(() -> VideoFilePolicy.check("a.mp4", sizeBytes, "video/mp4"))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({"0", "-1", "5368709121"})
  @DisplayName("[SPEC-003 R-4] 범위 밖 크기는 MEDIA_FILE_SIZE_INVALID")
  void rejectsSizeOutOfRange(long sizeBytes) {
    assertThatThrownBy(() -> VideoFilePolicy.check("a.mp4", sizeBytes, "video/mp4"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                org.assertj.core.api.Assertions.assertThat(e.getErrorCode())
                    .isEqualTo(MediaErrorCode.FILE_SIZE_INVALID));
  }
}
