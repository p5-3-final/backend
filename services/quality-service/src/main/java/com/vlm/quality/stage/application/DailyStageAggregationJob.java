package com.vlm.quality.stage.application;

import com.vlm.quality.shared.domain.SingleRunLock;
import com.vlm.quality.stage.domain.StageTimingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 전날(UTC) 측정 원본을 일 집계로 남긴다(SPEC-029 R-23). 다시 돌려도 같은 행이다. */
@Component
@RequiredArgsConstructor
public class DailyStageAggregationJob {

  /** advisory lock 키. 작업마다 다른 고정 값이다(SPEC 번호·요구사항 번호). */
  static final long LOCK_KEY = 29_023L;

  private static final Logger log = LoggerFactory.getLogger(DailyStageAggregationJob.class);

  private final StageTimingRepository timings;
  private final SingleRunLock lock;
  private final Clock clock;

  public boolean aggregateYesterday() {
    return aggregate(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1));
  }

  /** 하루를 집계한다. 다른 Pod가 돌고 있으면 false. */
  public boolean aggregate(LocalDate day) {
    AtomicInteger rows = new AtomicInteger();
    boolean ran = lock.runExclusively(LOCK_KEY, () -> rows.set(timings.aggregateDay(day)));
    if (ran) {
      log.info("Aggregated stage timings day={} rows={}", day, rows.get());
    }
    return ran;
  }
}
