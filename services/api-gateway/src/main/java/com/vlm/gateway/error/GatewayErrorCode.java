package com.vlm.gateway.error;

import com.vlm.common.error.ErrorCode;

/** api-gateway 오류 코드. 계약 {@code _common.yaml}과 {@code access-token.md}의 code 값과 같다. */
public enum GatewayErrorCode implements ErrorCode {
  TOKEN_MISSING(401, "AUTH_TOKEN_MISSING", "인증이 필요합니다."),
  TOKEN_INVALID(401, "AUTH_TOKEN_INVALID", "접근 토큰이 올바르지 않습니다."),
  TOKEN_EXPIRED(401, "AUTH_TOKEN_EXPIRED", "접근 토큰이 만료되었습니다.");

  private final int status;
  private final String code;
  private final String defaultMessage;

  GatewayErrorCode(int status, String code, String defaultMessage) {
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
