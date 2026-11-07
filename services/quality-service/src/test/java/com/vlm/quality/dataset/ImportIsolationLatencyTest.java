package com.vlm.quality.dataset;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.dataset.domain.ImportStatus;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ImportIsolationLatencyTest {

  private static final int VIDEOS = 1_000;
  private static final int REVIEW_ITEMS = 10_000;
  private static final int WARM_UP = 5;
  private static final int SAMPLES = 50;
  private static final int MEDIA_DELAY_MS = 50;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired DatasetImportService imports;
  @Autowired DatasetFixtures datasets;

  @Autowired
  @Qualifier("qualityBackgroundJobExecutor")
  ExecutorService background;

  @AfterEach
  void resetMedia() {
    TestcontainersConfiguration.media().resetAll();
  }

  @Test
  @DisplayName("[SPEC-030 NFR-2] 영상 1,000개 가져오기가 도는 동안에도 검토 목록 p95가 300ms 이하다")
  void reviewListStaysFast() throws Exception {
    insertReviewItems();
    String[] ids = externalIds();
    DatasetFixtures.upload("isolation", DatasetFixtures.manifest("isolation", ids));
    stubMedia(ids, MEDIA_DELAY_MS);
    UUID importId =
        imports
            .request(DatasetFixtures.manifestKey("isolation"), 0.2, 0, UUID.randomUUID())
            .importId();
    background.execute(datasets::runWorker);
    await()
        .atMost(Duration.ofSeconds(30))
        .until(() -> imports.get(importId).status() == ImportStatus.REGISTERING);

    List<Duration> samples = measure("/api/v1/quality/review-items?page=0&size=20");

    // 측정하는 동안 가져오기가 끝나지 않았어야 격리를 잰 것이다(1,000개 × 50ms = 50초).
    assertThat(imports.get(importId).status()).isEqualTo(ImportStatus.REGISTERING);
    assertThat(p95(samples)).as("review list p95").isLessThanOrEqualTo(Duration.ofMillis(300));
    // 남은 영상은 지연 없이 끝내 다음 테스트에 작업이 남지 않게 한다.
    stubMedia(ids, 0);
    await()
        .atMost(Duration.ofMinutes(2))
        .until(() -> !imports.get(importId).status().inProgress());
  }

  private void insertReviewItems() {
    jdbc.update(
        "INSERT INTO review_items (item_id, event_id, source_id, job_id, event_type, start_ms,"
            + " end_ms, judgment_method, taxonomy_version, reason, review_reasons,"
            + " evidence_frame_keys, analysis, status, created_at, updated_at)"
            + " SELECT gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),"
            + " 'fall', 1000, 4000, 'vlm', 'v2', 'review_required', '[]'::jsonb, '[]'::jsonb,"
            + " '{}'::jsonb, 'UNASSIGNED', now() - g * interval '1 second', now()"
            + " FROM generate_series(1, ?) AS g",
        REVIEW_ITEMS);
    jdbc.execute("ANALYZE review_items");
  }

  private static String[] externalIds() {
    String[] ids = new String[VIDEOS];
    for (int i = 0; i < VIDEOS; i++) {
      ids[i] = "iso-" + i;
    }
    return ids;
  }

  private static void stubMedia(String[] ids, int delayMs) {
    TestcontainersConfiguration.media().resetAll();
    for (String id : ids) {
      TestcontainersConfiguration.media()
          .stubFor(
              post(DatasetFixtures.MEDIA_PATH)
                  .withRequestBody(containing("\"externalId\":\"" + id + "\""))
                  .willReturn(
                      aResponse()
                          .withStatus(201)
                          .withFixedDelay(delayMs)
                          .withHeader("Content-Type", "application/json")
                          .withBody(
                              "{\"videoId\":\""
                                  + DatasetFixtures.videoIdOf(id)
                                  + "\",\"status\":\"UPLOADED\",\"sizeBytes\":1000}")));
    }
  }

  private List<Duration> measure(String path) throws Exception {
    List<Duration> samples = new ArrayList<>();
    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      long started = System.nanoTime();
      mvc.perform(operator(HttpMethod.GET, UUID.randomUUID(), path)).andExpect(status().isOk());
      if (i >= WARM_UP) {
        samples.add(Duration.ofNanos(System.nanoTime() - started));
      }
    }
    return samples;
  }

  private static Duration p95(List<Duration> samples) {
    List<Duration> sorted = samples.stream().sorted().toList();
    return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
  }
}
