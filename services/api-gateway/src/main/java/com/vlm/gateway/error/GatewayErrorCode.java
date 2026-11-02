package com.vlm.gateway.error;

import com.vlm.common.error.ErrorCode;

/** api-gateway 오류 코드. 계약 {@code _common.yaml}과 {@code access-token.md}의 code 값과 같다. */
public enum GatewayErrorCode implements ErrorCode {
  TOKEN_MISSING(401, "AUTH_TOKEN_MISSING", "인증이 필요합니다."),
  TOKEN_INVALID(401, "AUTH_TOKEN_INVALID", "접근 토큰이 올바르지 않습니다."),
  TOKEN_EXPIRED(401, "AUTH_TOKEN_EXPIRED", "접근 토큰이 만료되었습니다."),
  TICKET_INVALID(401, "AUTH_TICKET_INVALID", "알림 연결 티켓이 올바르지 않습니다."),
  RATE_LIMITED(429, "GATEWAY_RATE_LIMITED", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
  ROUTE_NOT_FOUND(404, "GATEWAY_ROUTE_NOT_FOUND", "요청한 경로를 찾을 수 없습니다."),
  TIMEOUT(504, "GATEWAY_TIMEOUT", "서비스가 제한 시간 안에 응답하지 않았습니다."),
  UNAVAILABLE(503, "GATEWAY_UNAVAILABLE", "서비스에 연결할 수 없습니다."),
  INTERNAL_ERROR(500, "GATEWAY_INTERNAL_ERROR", "요청을 처리하지 못했습니다.");

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
