package com.vlm.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.media.domain.NormalizationResult;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import com.vlm.media.support.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ConcurrentNormalizationTest {

  private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
  private static final int ROUNDS = 20;

  @Autowired NormalizationEventService service;
  @Autowired VideoRepository videos;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-007 NFR-6] 시작 신호와 결과가 동시에 와도 늦게 커밋된 시작 신호가 준비됨을 덮어쓰지 않는다")
  void startSignalNeverOverwritesResult() throws Exception {
    for (int round = 0; round < ROUNDS; round++) {
      UUID id = uploadedVideo();
      NormalizationOutcome ready = new NormalizationOutcome.Ready(id, result(id));

      runTogether(
          List.of(
              () -> {
                service.onNormalizing(id);
                return null;
              },
              () -> {
                service.onNormalized(ready);
                return null;
              }));

      assertThat(status(id)).as("round %d", round).isEqualTo("READY");
    }
  }

  @Test
  @DisplayName("[SPEC-007 NFR-6] 같은 결과가 동시에 10건 와도 오류 없이 한 번 반영된다")
  void sameResultTenTimes() throws Exception {
    UUID id = uploadedVideo();
    NormalizationOutcome ready = new NormalizationOutcome.Ready(id, result(id));
    List<Callable<Void>> calls = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      calls.add(
          () -> {
            service.onNormalized(ready);
            return null;
          });
    }

    runTogether(calls);

    assertThat(status(id)).isEqualTo("READY");
  }

  private static void runTogether(List<Callable<Void>> calls) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(calls.size());
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Void>> futures = new ArrayList<>();
      for (Callable<Void> call : calls) {
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return call.call();
                }));
      }
      start.countDown();
      for (Future<Void> future : futures) {
        future.get();
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private UUID uploadedVideo() {
    Video video =
        Video.startUpload(UUID.randomUUID(), "a.mp4", 1000, "video/mp4", NOW, Duration.ofHours(24));
    video.completeUpload(NOW);
    return videos.save(video).getPublicId();
  }

  private static NormalizationResult result(UUID sourceId) {
    UUID normalization = UUID.randomUUID();
    String base = "sources/" + sourceId + "/normalizations/" + normalization + "/";
    return new NormalizationResult(
        normalization, "normalize-v1", base + "normalized.mp4", base + "hls/index.m3u8", 5_000);
  }

  private String status(UUID id) {
    return jdbc.queryForObject("SELECT status FROM videos WHERE public_id = ?", String.class, id);
  }
}
