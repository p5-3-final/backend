package com.vlm.media.retention.application;

import com.vlm.media.retention.domain.RetainedVideoRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보존 만료 시각이 지난 최소 기록을 지우는 주기 작업(SPEC-018 R-17). 각 서비스는 {@code retainUntil}로 자기 보존 데이터를 따로 지우므로 철회
 * 이벤트를 내지 않는다.
 */
@Service
@RequiredArgsConstructor
public class RetentionExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(RetentionExpiryJob.class);
  private static final int BATCH_SIZE = 500;

  private final RetainedVideoRepository retainedVideoRepository;
  private final Clock clock;

  /**
   * 만료된 기록을 한 묶음 지운다. 여러 Pod가 동시에 돌려도 행 잠금(SKIP LOCKED)으로 나눠 가진다.
   *
   * @return 지운 기록 수
   */
  @Transactional
  public int expire() {
    int deleted = retainedVideoRepository.deleteExpired(clock.instant(), BATCH_SIZE);
    if (deleted > 0) {
      log.info("expired retained video records: {}", deleted);
    }
    return deleted;
  }
}
