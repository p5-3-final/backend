package com.vlm.auth.domain;

import com.vlm.common.error.ErrorCode;

/** auth-service 오류 코드. 계약 {@code auth-service.yaml}의 code 값과 같다. */
public enum AuthErrorCode implements ErrorCode {
  EMAIL_DUPLICATED(409, "AUTH_EMAIL_DUPLICATED", "이미 가입된 이메일입니다."),
  PASSWORD_POLICY(400, "AUTH_PASSWORD_POLICY", "비밀번호는 8~64자이고 영문과 숫자를 모두 포함해야 합니다."),
  INVALID_CREDENTIALS(401, "AUTH_INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."),
  ACCOUNT_LOCKED(423, "AUTH_ACCOUNT_LOCKED", "로그인 실패가 반복되어 계정이 잠겼습니다. 잠시 후 다시 시도해 주세요."),
  TOKEN_MISSING(401, "AUTH_TOKEN_MISSING", "인증이 필요합니다."),
  TOKEN_INVALID(401, "AUTH_TOKEN_INVALID", "인증 정보가 올바르지 않습니다."),
  REFRESH_INVALID(401, "AUTH_REFRESH_INVALID", "다시 로그인해 주세요."),
  REFRESH_REUSED(401, "AUTH_REFRESH_REUSED", "다시 로그인해 주세요.");

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
