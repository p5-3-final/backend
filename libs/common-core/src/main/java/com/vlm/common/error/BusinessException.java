package com.vlm.common.error;

/** 비즈니스 규칙 위반을 나타내는 예외. 공통 오류 핸들러가 {@link ErrorCode}로 ProblemDetail을 만든다. */
public class BusinessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  // ErrorCode 구현은 보통 enum이지만 인터페이스라 직렬화를 보장할 수 없다. 예외를 직렬화해서 보내는 경로가 없으므로 제외한다.
  private final transient ErrorCode errorCode;

  public BusinessException(ErrorCode errorCode) {
    this(errorCode, errorCode.defaultMessage());
  }

  public BusinessException(ErrorCode errorCode, String detail) {
    super(detail);
    this.errorCode = errorCode;
  }

  public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
    super(detail, cause);
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }
}
