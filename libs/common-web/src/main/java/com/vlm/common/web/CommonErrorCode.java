package com.vlm.common.web;

import com.vlm.common.error.ErrorCode;

/** 특정 서비스에 속하지 않는 공통 오류 코드. */
public enum CommonErrorCode implements ErrorCode {
  INVALID_REQUEST(400, "COMMON_INVALID_REQUEST", "요청 형식이 올바르지 않습니다."),
  INTERNAL_ERROR(500, "COMMON_INTERNAL_ERROR", "요청을 처리하지 못했습니다.");

  private final int status;
  private final String code;
  private final String defaultMessage;

  CommonErrorCode(int status, String code, String defaultMessage) {
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
