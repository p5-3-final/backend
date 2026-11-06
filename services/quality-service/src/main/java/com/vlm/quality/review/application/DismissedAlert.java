package com.vlm.quality.review.application;

import java.util.List;
import java.util.UUID;

/**
 * 검토 큐에 넣을 dismissed 긴급 알림(SPEC-029 R-28).
 *
 * @param eventId dismissed alert.raised의 eventId. 검토 항목 ID를 이 값으로 정한다
 * @param observedMs 알림을 낸 영상 시각(밀리초). 검토 구간의 시작·끝으로 쓴다
 * @param dismissReason rejected·unverified·unknown
 */
public record DismissedAlert(
    UUID eventId,
    UUID sourceId,
    UUID jobId,
    String candidateEventType,
    long observedMs,
    String dismissReason,
    String taxonomyVersion,
    List<String> evidenceFrameKeys) {

  public DismissedAlert {
    evidenceFrameKeys = List.copyOf(evidenceFrameKeys);
  }
}
