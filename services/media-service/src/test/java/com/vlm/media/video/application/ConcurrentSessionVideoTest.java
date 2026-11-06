package com.vlm.media.video.application;

import static com.vlm.media.support.MediaApi.createSession;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.SessionRole;
import com.vlm.media.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
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
class ConcurrentSessionVideoTest {

  private static final int REQUESTS = 10;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CreateVideoService createVideoService;

  @Test
  @DisplayName("[SPEC-017 NFR-2] 영상 99개인 세션에 업로드 자리 요청이 동시에 10건 와도 1건만 들어간다")
  void keepsSessionLimitUnderConcurrency() throws Exception {
    UUID ownerId = UUID.randomUUID();
    UUID sessionId = UUID.fromString(createSession(mvc, ownerId, "세션"));
    jdbc.update(
        """
        INSERT INTO videos (public_id, owner_id, file_name, size_bytes, content_type, status,
            storage_key, expires_at, session_id, session_role)
        SELECT gen_random_uuid(), ?, 'v.mp4', 1, 'video/mp4', 'UPLOADED', 'k', now(), ?, 'DURING'
        FROM generate_series(1, 99)
        """,
        ownerId,
        sessionId);

    CountDownLatch start = new CountDownLatch(1);
    List<Future<?>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return createVideoService.create(
                      ownerId, "a.mp4", 1000, "video/mp4", sessionId, SessionRole.AFTER);
                }));
      }
      start.countDown();
      int full = 0;
      for (Future<?> result : results) {
        try {
          result.get();
        } catch (ExecutionException e) {
          assertThat(e.getCause())
              .isInstanceOfSatisfying(
                  BusinessException.class,
                  b -> assertThat(b.getErrorCode()).isEqualTo(MediaErrorCode.SESSION_FULL));
          full++;
        }
      }
      assertThat(full).isEqualTo(REQUESTS - 1);
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM videos WHERE session_id = ?", Integer.class, sessionId))
        .isEqualTo(100);
  }
}
