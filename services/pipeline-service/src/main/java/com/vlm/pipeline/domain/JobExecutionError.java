package com.vlm.pipeline.domain;

import com.vlm.common.error.ErrorCode;

public enum JobExecutionError implements ErrorCode {
  JOB_NOT_FOUND;

  @Override
  public int status() {
    return 404;
  }

  @Override
  public String code() {
    return "PIPELINE_JOB_NOT_FOUND";
  }

  @Override
  public String defaultMessage() {
    return "작업을 찾을 수 없습니다.";
  }
}
