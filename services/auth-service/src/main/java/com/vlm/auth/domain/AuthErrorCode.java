package com.vlm.auth.domain;

import com.vlm.common.error.ErrorCode;

/** auth-service 오류 코드. 계약 {@code auth-service.yaml}의 code 값과 같다. */
public enum AuthErrorCode implements ErrorCode {
  EMAIL_DUPLICATED(409, "AUTH_EMAIL_DUPLICATED", "이미 가입된 이메일입니다."),
  PASSWORD_POLICY(400, "AUTH_PASSWORD_POLICY", "비밀번호는 8~64자이고 영문과 숫자를 모두 포함해야 합니다.");

  private final int status;
  private final String code;
  private final String defaultMessage;

  AuthErrorCode(int status, String code, String defaultMessage) {
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
