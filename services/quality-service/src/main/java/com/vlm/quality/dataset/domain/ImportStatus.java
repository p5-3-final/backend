package com.vlm.quality.dataset.domain;

/** 가져오기 상태(계약 {@code DatasetImport.status}). */
public enum ImportStatus {
  PENDING,
  VALIDATING,
  REGISTERING,
  COMPLETED,
  FAILED;

  /** 작업 스레드가 맡았거나 맡을 상태. 같은 매니페스트를 다시 요청하면 409다(R-1). */
  public boolean inProgress() {
    return this == PENDING || this == VALIDATING || this == REGISTERING;
  }
}
