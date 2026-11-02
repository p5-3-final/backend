package com.vlm.media.domain;

import com.vlm.common.error.BusinessException;
import java.util.Locale;
import java.util.Map;

/** 업로드할 수 있는 영상 파일 조건(SPEC-003 R-4, R-5). */
public final class VideoFilePolicy {

  /** SPEC-003 R-4: 최대 5GB. */
  public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024 * 1024;

  // SPEC-003 R-5: 확장자와 MIME 형식이 둘 다 맞아야 한다. 계약 VideoContentType과 같은 목록이다.
  private static final Map<String, String> CONTENT_TYPE_BY_EXTENSION =
      Map.of(
          "mp4", "video/mp4",
          "mov", "video/quicktime",
          "mkv", "video/x-matroska",
          "avi", "video/x-msvideo");

  private VideoFilePolicy() {}

  /** 조건에 맞지 않으면 {@link BusinessException}을 던진다. */
  public static void check(String fileName, long sizeBytes, String contentType) {
    if (sizeBytes < 1 || sizeBytes > MAX_SIZE_BYTES) {
      throw new BusinessException(MediaErrorCode.FILE_SIZE_INVALID);
    }
    int dot = fileName.lastIndexOf('.');
    String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    if (!contentType.equals(CONTENT_TYPE_BY_EXTENSION.get(extension))) {
      throw new BusinessException(MediaErrorCode.UNSUPPORTED_FORMAT);
    }
  }
}
