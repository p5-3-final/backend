package com.vlm.media.dataset.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StoragePuts;
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
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class ConcurrentDatasetVideoTest {

  private static final int REQUESTS = 5;

  @Autowired DatasetVideoService datasetVideos;
  @Autowired JdbcTemplate jdbc;
  @Autowired S3Client s3;

  @Test
  @DisplayName("[SPEC-030 R-6] 같은 등록 요청 5건이 동시에 와도 영상과 video.uploaded는 하나이고 모두 같은 영상을 받는다")
  void registersOnceUnderConcurrency() throws Exception {
    String importKey = "imports/ds-c/videos/clip-0001.mp4";
    StoragePuts.putImport(s3, importKey, new byte[1000]);
    var command =
        new RegisterDatasetVideoCommand(
            UUID.randomUUID(), "clip-0001", UUID.randomUUID(), importKey, "video/mp4", null);

    CountDownLatch start = new CountDownLatch(1);
    List<Future<RegisteredDatasetVideo>> results = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return datasetVideos.register(command);
                }));
      }
      start.countDown();
      List<UUID> ids = new ArrayList<>();
      int created = 0;
      for (Future<RegisteredDatasetVideo> result : results) {
        RegisteredDatasetVideo registered = result.get();
        ids.add(registered.video().getPublicId());
        created += registered.created() ? 1 : 0;
      }
      assertThat(ids).containsOnly(ids.get(0));
      assertThat(created).isEqualTo(1);
    }

    assertThat(jdbc.queryForObject("SELECT count(*) FROM videos", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class))
        .isEqualTo(1);
  }
}
