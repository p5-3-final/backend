package com.vlm.quality.shared.domain;

import com.vlm.common.error.ErrorCode;

/** quality-service 오류 코드(계약 openapi/quality-service.yaml). */
public enum QualityErrorCode implements ErrorCode {
  INVALID_REQUEST(400, "QUALITY_INVALID_REQUEST", "요청 형식이 올바르지 않습니다."),
  FORBIDDEN(403, "QUALITY_FORBIDDEN", "운영자만 쓸 수 있습니다."),
  CALLER_NOT_ALLOWED(403, "QUALITY_CALLER_NOT_ALLOWED", "허용된 호출자가 아닙니다."),
  REVIEW_NOT_FOUND(404, "QUALITY_REVIEW_NOT_FOUND", "검토 항목이 없습니다."),
  REVIEW_ALREADY_ASSIGNED(409, "QUALITY_REVIEW_ALREADY_ASSIGNED", "이미 다른 사람이 맡은 항목입니다."),
  REVIEW_ALREADY_LABELED(409, "QUALITY_REVIEW_ALREADY_LABELED", "라벨이 끝난 항목입니다."),
  REVIEW_NOT_ASSIGNED(409, "QUALITY_REVIEW_NOT_ASSIGNED", "맡은 상태가 아닌 항목입니다."),
  REVIEW_NOT_ASSIGNEE(403, "QUALITY_REVIEW_NOT_ASSIGNEE", "항목을 맡은 사람이 아닙니다."),
  REVIEW_NOT_LABELED(409, "QUALITY_REVIEW_NOT_LABELED", "Golden Set에 넣을 수 있는 라벨이 없습니다."),
  GOLDEN_SET_NOT_FOUND(404, "QUALITY_GOLDEN_SET_NOT_FOUND", "Golden Set이 없습니다."),
  GOLDEN_ENTRY_NOT_FOUND(404, "QUALITY_GOLDEN_ENTRY_NOT_FOUND", "초안 항목이 없습니다."),
  GOLDEN_SET_DUPLICATED(409, "QUALITY_GOLDEN_SET_DUPLICATED", "같은 이름의 Golden Set이 있습니다."),
  GOLDEN_ENTRY_DUPLICATED(409, "QUALITY_GOLDEN_ENTRY_DUPLICATED", "이미 초안에 든 항목입니다."),
  GOLDEN_ORIGIN_MISMATCH(409, "QUALITY_GOLDEN_ORIGIN_MISMATCH", "검토 라벨 Golden Set이 아닙니다."),
  GOLDEN_EMPTY(409, "QUALITY_GOLDEN_EMPTY", "초안이 비어 있습니다."),
  GOLDEN_FREEZE_IN_PROGRESS(409, "QUALITY_GOLDEN_FREEZE_IN_PROGRESS", "다른 확정이 진행 중입니다."),
  RUN_NOT_FOUND(404, "QUALITY_RUN_NOT_FOUND", "평가 실행이 없습니다."),
  RUN_SET_MISMATCH(409, "QUALITY_RUN_SET_MISMATCH", "다른 Golden Set으로 잰 실행입니다."),
  SOURCE_NOT_FOUND(404, "QUALITY_SOURCE_NOT_FOUND", "그 영상의 측정 기록이 없습니다."),
  IMPORT_NOT_FOUND(404, "QUALITY_IMPORT_NOT_FOUND", "가져오기가 없습니다."),
  IMPORT_IN_PROGRESS(409, "QUALITY_IMPORT_IN_PROGRESS", "같은 매니페스트의 가져오기가 진행 중입니다."),
  EXPORT_NOT_FOUND(404, "QUALITY_EXPORT_NOT_FOUND", "내보내기가 없습니다.");

  private final int status;
  private final String code;
  private final String defaultMessage;

  QualityErrorCode(int status, String code, String defaultMessage) {
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
