package com.vlm.quality.shared.domain;

import org.jspecify.annotations.Nullable;

/**
 * 검토자가 붙인 정답 라벨(계약 ReviewLabel).
 *
 * @param verdict 판정 결과가 맞았는지: correct·incorrect·uncertain
 * @param eventPresent 그 구간에 이벤트가 실제로 있었는지
 * @param eventType 실제 유형. eventPresent가 false면 null
 * @param startMs 실제 시작(밀리초)
 * @param endMs 실제 끝(밀리초)
 * @param severity low·medium·high 또는 null
 * @param note 메모
 */
public record ReviewLabel(
    String verdict,
    boolean eventPresent,
    @Nullable String eventType,
    @Nullable Long startMs,
    @Nullable Long endMs,
    @Nullable String severity,
    @Nullable String note) {

  public static final String UNCERTAIN = "uncertain";

  /** Golden Set에 넣을 수 있는 확정 라벨인지(R-12). */
  public boolean hasDecidedVerdict() {
    return !UNCERTAIN.equals(verdict);
  }
}
