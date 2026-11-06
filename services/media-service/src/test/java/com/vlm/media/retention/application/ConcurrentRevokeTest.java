package com.vlm.media.retention.application;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ConcurrentRevokeTest {

  private static final int REQUESTS = 10;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired RetentionService retentionService;

  @Test
  @DisplayName("[SPEC-018 NFR-3] 같은 기록에 철회가 동시에 10건 와도 철회 이벤트는 한 번이다")
  void revokesOnceUnderConcurrency() throws Exception {
    UUID ownerId = UUID.randomUUID();
    String videoId =
        MediaApi.json(
                mvc.perform(
                    asUser(
                        createVideoRequest(
                            "a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                        ownerId)))
            .at("/video/id")
            .asText();
    mvc.perform(asUser(deleteRequest(videoId), ownerId));
    UUID sourceId = UUID.fromString(videoId);

    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  retentionService.revoke(sourceId, ownerId);
                  return null;
                }));
      }
      start.countDown();
      int notFound = 0;
      for (Future<?> result : results) {
        try {
          result.get();
        } catch (ExecutionException e) {
          assertThat(e.getCause())
              .isInstanceOfSatisfying(
                  BusinessException.class,
                  b ->
                      assertThat(b.getErrorCode())
                          .isEqualTo(MediaErrorCode.RETAINED_VIDEO_NOT_FOUND));
          notFound++;
        }
      }
      assertThat(notFound).isEqualTo(REQUESTS - 1);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = 'video.retention.revoked'",
                Integer.class))
        .isEqualTo(1);
  }
}
