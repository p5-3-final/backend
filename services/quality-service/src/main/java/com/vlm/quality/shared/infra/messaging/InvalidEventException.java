package com.vlm.quality.shared.infra.messaging;

/** 받은 메시지가 계약과 맞지 않는다. 다시 받아도 같으므로 재시도하지 않고 건너뛴다. */
public class InvalidEventException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public InvalidEventException(String message) {
    super(message);
  }

  public InvalidEventException(String message, Throwable cause) {
    super(message, cause);
  }
}
