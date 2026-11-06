package com.vlm.media.shared.domain;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;

import java.util.Comparator;

/** 작업 세션 안 영상 순서(SPEC-017 R-7). Aggregator가 "직전 영상"을 고르는 기준과 같다. */
public final class SessionOrder {

  /**
   * 역할 순서(작업 전 → 중 → 후), 같은 역할 안에서는 업로드 완료 시각 순. 완료하지 않은 영상은 그 역할의 끝에 만든 시각 순으로 놓는다. 마지막은 내부 id로
   * 순서를 고정한다.
   */
  public static final Comparator<Video> COMPARATOR =
      Comparator.<Video, SessionRole>comparing(
              Video::getSessionRole, nullsLast(Comparator.<SessionRole>naturalOrder()))
          .thenComparing(Video::getUploadedAt, nullsLast(naturalOrder()))
          .thenComparing(Video::getCreatedAt)
          .thenComparing(Video::getId, nullsLast(naturalOrder()));

  private SessionOrder() {}
}
