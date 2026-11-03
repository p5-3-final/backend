package com.vlm.pipeline.domain;

import com.vlm.common.error.ErrorCode;

/** 내부 계획 요청 충돌. 아직 외부 API 오류 계약은 아니다. */
public enum PlanningError implements ErrorCode {
  INPUT_CONFLICT,
  ACTIVE_JOB_EXISTS;

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
    return this == INPUT_CONFLICT ? "같은 계획 식별자의 입력이 다릅니다." : "영상에 활성 작업이 있습니다.";
  }
}
