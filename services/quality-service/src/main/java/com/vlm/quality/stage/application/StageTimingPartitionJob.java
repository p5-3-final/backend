package com.vlm.quality.stage.application;

import com.vlm.quality.shared.domain.SingleRunLock;
import com.vlm.quality.stage.domain.StageTimingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 측정 원본 월 파티션 관리(SPEC-029 R-25).
 *
 * <p>이번 달부터 3개월 뒤까지 미리 만들어 미래 시각 기록이 들어갈 곳이 없어지지 않게 하고, 상한이 보존 기간 전인 파티션을 통째로 지운다. 월 단위라 원본은 최대 약
 * 120일 남지만 조회는 모두 90일로 거른다. 일 집계는 다른 표라 남는다.
 */
@Component
@RequiredArgsConstructor
public class StageTimingPartitionJob {

  /** advisory lock 키. 작업마다 다른 고정 값이다(SPEC 번호·요구사항 번호). */
  static final long LOCK_KEY = 29_025L;

  private static final int MONTHS_AHEAD = 3;
  private static final Logger log = LoggerFactory.getLogger(StageTimingPartitionJob.class);

  private final StageTimingRepository timings;
  private final SingleRunLock lock;
  private final StageProperties properties;
  private final Clock clock;

  public void manage() {
    manage(clock.instant());
  }

  /** 기준 시각으로 파티션을 맞춘다. 다른 Pod가 돌고 있으면 아무것도 하지 않는다. */
  public void manage(Instant now) {
    List<String> dropped = new ArrayList<>();
    lock.runExclusively(
        LOCK_KEY,
        () -> {
          YearMonth current = YearMonth.from(now.atZone(ZoneOffset.UTC));
          for (int i = 0; i <= MONTHS_AHEAD; i++) {
            timings.createPartition(current.plusMonths(i));
          }
          dropped.addAll(timings.dropPartitionsEndingBefore(now.minus(properties.retention())));
        });
    if (!dropped.isEmpty()) {
      log.info("Dropped expired stage timing partitions {}", dropped);
    }
  }
}
