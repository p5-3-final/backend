package com.vlm.pipeline.domain;

import java.util.UUID;

/** 종료 증거는 운영자의 외부 확인 참조다. 타임아웃만으로 자동 생성하지 않는다. */
public record StopConfirmation(UUID executionId, UUID confirmationId, String evidence) {
  public StopConfirmation {
    if (evidence.isBlank()
        || evidence.length() > 500
        || evidence.indexOf('\n') >= 0
        || evidence.indexOf('\r') >= 0)
      throw new IllegalArgumentException(
          "Stop evidence must be a single reference of 1 to 500 characters");
  }

  public static StopConfirmation parse(String[] args) {
    if (args.length != 3)
      throw new IllegalArgumentException("Required: executionId confirmationId evidence-reference");
    UUID executionId = UUID.fromString(args[0]), confirmationId = UUID.fromString(args[1]);
    if (!executionId.toString().equals(args[0]) || !confirmationId.toString().equals(args[1]))
      throw new IllegalArgumentException("Identifiers must be canonical UUIDs");
    return new StopConfirmation(executionId, confirmationId, args[2]);
  }
}
