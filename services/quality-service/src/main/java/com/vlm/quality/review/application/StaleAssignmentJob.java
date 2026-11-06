package com.vlm.quality.review.application;

import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.shared.domain.SingleRunLock;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 라벨 없이 오래 맡아 둔 항목을 큐로 되돌린다(SPEC-029 R-10). 여러 Pod 중 한 곳만 돈다. */
@Component
@RequiredArgsConstructor
public class StaleAssignmentJob {

  /** advisory lock 키. 작업마다 다른 고정 값이다(SPEC 번호·요구사항 번호). */
  static final long LOCK_KEY = 29_010L;

  private static final Logger log = LoggerFactory.getLogger(StaleAssignmentJob.class);

  private final ReviewItemRepository items;
  private final SingleRunLock lock;
  private final ReviewProperties properties;
  private final Clock clock;

  /** 되돌린 항목 수. 다른 Pod가 돌고 있으면 0이다. */
  public int releaseStale() {
    AtomicInteger released = new AtomicInteger();
    boolean ran =
        lock.runExclusively(
            LOCK_KEY,
            () -> {
              Instant now = clock.instant();
              released.set(
                  items.releaseAssignedBefore(now.minus(properties.assignmentTimeout()), now));
            });
    if (ran) {
      log.info("Released stale review assignments count={}", released.get());
    }
    return released.get();
  }
}
