package com.vlm.pipeline.domain;

import com.vlm.common.error.ErrorCode;

/** 입력을 덮어쓰거나 성공 처리하지 않는 수신 충돌. */
public enum IntakeError implements ErrorCode {
  EVENT_CONFLICT,
  NORMALIZATION_CONFLICT,
  SETTINGS_CONFLICT;

  @Override
  public int status() {
    return 409;
  }

  @Override
  public String code() {
    return "PIPELINE_" + name();
  }

  @Override
  public String defaultMessage() {
    return "같은 식별자의 정규화 입력 또는 설정이 다릅니다.";
  }
}
