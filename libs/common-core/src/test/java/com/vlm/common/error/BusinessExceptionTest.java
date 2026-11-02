package com.vlm.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

  private enum SampleErrorCode implements ErrorCode {
    DUPLICATED(409, "SAMPLE_DUPLICATED", "이미 존재합니다.");

    private final int status;
    private final String code;
    private final String defaultMessage;

    SampleErrorCode(int status, String code, String defaultMessage) {
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

  @Test
  @DisplayName("상세 메시지가 없으면 오류 코드의 기본 메시지를 쓴다")
  void usesDefaultMessage() {
    BusinessException exception = new BusinessException(SampleErrorCode.DUPLICATED);

    assertThat(exception.getErrorCode()).isEqualTo(SampleErrorCode.DUPLICATED);
    assertThat(exception.getMessage()).isEqualTo("이미 존재합니다.");
  }

  @Test
  @DisplayName("상세 메시지를 주면 그 메시지를 쓴다")
  void usesGivenDetail() {
    BusinessException exception =
        new BusinessException(SampleErrorCode.DUPLICATED, "user@example.com은 이미 가입되어 있습니다.");

    assertThat(exception.getMessage()).isEqualTo("user@example.com은 이미 가입되어 있습니다.");
  }

  @Test
  @DisplayName("원인 예외를 보존한다")
  void keepsCause() {
    IllegalStateException cause = new IllegalStateException("db");

    BusinessException exception = new BusinessException(SampleErrorCode.DUPLICATED, "중복", cause);

    assertThat(exception.getCause()).isSameAs(cause);
  }
}
