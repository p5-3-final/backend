package com.vlm.quality.export.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 영상별로 가장 최근에 받은 결과 파일(SPEC-030 R-13).
 *
 * @param receivedAt 받은 시각. R-14 기간 선택은 이 시각의 UTC 날짜를 쓴다.
 */
public record SourceResult(
    UUID sourceId, UUID jobId, int revision, String resultKey, Instant receivedAt) {

  public LocalDate receivedOn() {
    return LocalDate.ofInstant(receivedAt, ZoneOffset.UTC);
  }
}
