package com.vlm.quality.config;

import com.vlm.quality.dataset.application.ImportWorker;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** 맡을 가져오기·내보내기가 있으면 전용 실행기에 한 번에 하나만 올린다(SPEC-030 NFR-2). */
@Component
class BackgroundJobs {

  private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);

  private final ImportWorker imports;
  private final ExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();

  BackgroundJobs(
      ImportWorker imports, @Qualifier(BackgroundJobConfig.EXECUTOR) ExecutorService executor) {
    this.imports = imports;
    this.executor = executor;
  }

  /**
   * 실행기가 쉬고 있으면 일을 올린다. 이미 돌고 있으면 아무것도 하지 않는다.
   *
   * @return 일을 올렸으면 true
   */
  boolean trigger() {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    try {
      executor.execute(this::drain);
      return true;
    } catch (RejectedExecutionException e) {
      running.set(false);
      log.warn("Background job executor rejected work", e);
      return false;
    }
  }

  private void drain() {
    try {
      boolean worked = true;
      while (worked) {
        worked = imports.runOnce();
      }
    } catch (RuntimeException e) {
      // DB 장애 등. 행은 진행 중으로 남아 heartbeat가 끊기면 다음 주기나 다른 Pod가 이어받는다.
      log.error("Background job failed", e);
    } finally {
      running.set(false);
    }
  }
}
