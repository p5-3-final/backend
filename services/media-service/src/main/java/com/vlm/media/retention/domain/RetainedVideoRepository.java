package com.vlm.media.retention.domain;

import java.time.Instant;

public interface RetainedVideoRepository {

  RetainedVideo save(RetainedVideo retainedVideo);

  /** 보존 만료 시각이 지난 기록을 다른 트랜잭션이 잠근 행은 건너뛰고 최대 {@code limit}개 지운다. */
  int deleteExpired(Instant now, int limit);
}
