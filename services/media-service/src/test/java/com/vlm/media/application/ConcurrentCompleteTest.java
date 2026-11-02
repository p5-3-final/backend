package com.vlm.media.application;

import static com.vlm.media.support.MediaApi.createVideo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.domain.VideoStatus;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ConcurrentCompleteTest {

  private static final int REQUESTS = 10;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired CompleteUploadService completeUploadService;

  @Test
  @DisplayName("[SPEC-003 NFR-2] 같은 영상에 완료 요청이 동시에 10건 와도 상태 전이와 이벤트는 한 번이다")
  void completesOnceUnderConcurrency() throws Exception {
    UUID ownerId = UUID.randomUUID();
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    UUID videoId = UUID.fromString(created.get("video").get("id").asText());

    CountDownLatch start = new CountDownLatch(1);
    List<Future<VideoStatus>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return completeUploadService.complete(videoId, ownerId, List.of()).getStatus();
                }));
      }
      start.countDown();
      for (Future<VideoStatus> result : results) {
        assertThat(result.get()).isEqualTo(VideoStatus.UPLOADED);
      }
    }

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                videoId))
        .isEqualTo(1);
  }
}
