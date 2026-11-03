package com.vlm.media.domain;

import com.vlm.common.error.ErrorCode;

/** media-service 오류 코드. 계약 {@code media-service.yaml}의 code 값과 같다. */
public enum MediaErrorCode implements ErrorCode {
  // 게이트웨이를 거치지 않은 요청. 계약 _common.yaml Unauthenticated와 같은 코드를 쓴다.
  TOKEN_MISSING(401, "AUTH_TOKEN_MISSING", "인증이 필요합니다."),
  TOKEN_INVALID(401, "AUTH_TOKEN_INVALID", "인증 정보가 올바르지 않습니다."),
  FILE_SIZE_INVALID(400, "MEDIA_FILE_SIZE_INVALID", "파일 크기는 1바이트 이상 5GB 이하여야 합니다."),
  UNSUPPORTED_FORMAT(400, "MEDIA_UNSUPPORTED_FORMAT", "mp4, mov, mkv, avi 영상만 올릴 수 있습니다."),
  VIDEO_NOT_FOUND(404, "MEDIA_VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다."),
  UPLOAD_INCOMPLETE(409, "MEDIA_UPLOAD_INCOMPLETE", "업로드가 아직 끝나지 않았습니다."),
  INVALID_STATE(409, "MEDIA_INVALID_STATE", "지금 상태에서는 업로드를 완료할 수 없습니다."),
  NOT_PLAYABLE(409, "MEDIA_NOT_PLAYABLE", "업로드가 끝난 영상만 재생할 수 있습니다.");

  private final int status;
  private final String code;
  private final String defaultMessage;

  MediaErrorCode(int status, String code, String defaultMessage) {
    this.status = status;
    this.code = code;
    this.defaultMessage = defaultMessage;
  }

  @Override
  public int status() {
    return status;
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public String defaultMessage() {
    return defaultMessage;
  }
}
