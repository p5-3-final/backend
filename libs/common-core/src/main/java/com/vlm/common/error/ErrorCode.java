package com.vlm.common.error;

/**
 * 서비스별 오류 코드의 공통 계약. 각 서비스는 이 인터페이스를 구현하는 enum으로 오류 코드를 정의한다.
 *
 * <p>오류 응답(RFC 9457 ProblemDetail)의 {@code status}와 {@code code} 필드가 이 값에서 나온다.
 */
public interface ErrorCode {

  /** 응답 HTTP 상태 코드. */
  int status();

  /** {@code <DOMAIN>_<REASON>} 형식의 오류 코드. 예: {@code AUTH_EMAIL_DUPLICATED}. */
  String code();

  /** 상세 메시지를 따로 주지 않았을 때 쓰는 사용자용 메시지(한국어). */
  String defaultMessage();
}
