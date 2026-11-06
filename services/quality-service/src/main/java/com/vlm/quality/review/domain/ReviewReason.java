package com.vlm.quality.review.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 검토 항목이 큐에 들어온 사유(계약 ReviewItem.reason의 {@code x-extensible-enum}, SPEC-029 R-1·R-2·R-27·R-28).
 *
 * <p>계약은 열린 목록이라 생성 모델이 {@code String}이다. 서버가 아는 값은 이 목록이고, 계약 테스트가 목록이 같은지 확인한다.
 */
public enum ReviewReason {
  REVIEW_REQUIRED("review_required"),
  SAMPLE("sample"),
  BUDGET_EXCEEDED("budget_exceeded"),
  CALL_CAP_REACHED("call_cap_reached"),
  MODEL_UNAVAILABLE("model_unavailable"),
  ALERT_DISMISSED("alert_dismissed");

  private final String wire;

  ReviewReason(String wire) {
    this.wire = wire;
  }

  /** 계약에 쓰는 값. */
  public String wire() {
    return wire;
  }

  /** 계약 값으로 찾는다. 모르는 값이면 비어 있다. */
  public static Optional<ReviewReason> fromWire(String value) {
    return Arrays.stream(values()).filter(r -> r.wire.equals(value)).findFirst();
  }
}
