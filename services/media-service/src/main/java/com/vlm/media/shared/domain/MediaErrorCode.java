package com.vlm.media.shared.domain;

import com.vlm.common.error.ErrorCode;

/** media-service 오류 코드. 계약 {@code media-service.yaml}의 code 값과 같다. */
public enum MediaErrorCode implements ErrorCode {
  FILE_SIZE_INVALID(400, "MEDIA_FILE_SIZE_INVALID", "파일 크기는 1바이트 이상 5GB 이하여야 합니다."),
  UNSUPPORTED_FORMAT(400, "MEDIA_UNSUPPORTED_FORMAT", "mp4, mov, mkv, avi 영상만 올릴 수 있습니다."),
  VIDEO_NOT_FOUND(404, "MEDIA_VIDEO_NOT_FOUND", "영상을 찾을 수 없습니다."),
  SESSION_NOT_FOUND(404, "MEDIA_SESSION_NOT_FOUND", "작업 세션을 찾을 수 없습니다."),
  IMPORT_OBJECT_NOT_FOUND(404, "MEDIA_IMPORT_OBJECT_NOT_FOUND", "가져오기 버킷에 파일이 없습니다."),
  CALLER_NOT_ALLOWED(403, "MEDIA_CALLER_NOT_ALLOWED", "허용된 호출자가 아닙니다."),
  RETAINED_VIDEO_NOT_FOUND(404, "MEDIA_RETAINED_VIDEO_NOT_FOUND", "보존 중인 영상을 찾을 수 없습니다."),
  UPLOAD_INCOMPLETE(409, "MEDIA_UPLOAD_INCOMPLETE", "업로드가 아직 끝나지 않았습니다."),
  SESSION_FULL(409, "MEDIA_SESSION_FULL", "세션에는 영상을 100개까지 넣을 수 있습니다."),
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
