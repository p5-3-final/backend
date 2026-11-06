package com.vlm.quality;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.StageTimingBatch;
import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.stage.domain.StageTiming;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class QualityLatencyTest {

  // 처음 몇 번은 JIT·연결 준비로 느려서 따로 버린다.
  private static final int WARM_UP = 5;
  private static final int SAMPLES = 50;
  private static final int REVIEW_ITEMS = 10_000;
  private static final int TIMINGS = 5_000;
  // 경로 5개에 나눠 담는다. 영상 하나가 여러 단계를 거치는 실제 리포트와 비슷하게 맞춘다.
  private static final String[] STAGES = {
    "analyzer.judge", "analyzer.prefilter", "normalize.handle", "chunk.detect", "loader.handle"
  };

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired StageTimingIngestService ingest;

  @Test
  @DisplayName("[SPEC-029 NFR-2] 검토 항목 10,000개에서 목록 조회 p95가 300ms 이하다")
  void reviewListP95() throws Exception {
    jdbc.update(
        "INSERT INTO review_items (item_id, event_id, source_id, job_id, event_type, start_ms,"
            + " end_ms, judgment_method, taxonomy_version, reason, review_reasons,"
            + " evidence_frame_keys, analysis, status, created_at, updated_at)"
            + " SELECT gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), gen_random_uuid(),"
            + " 'fall', 1000, 4000, 'vlm', 'v2',"
            + " CASE WHEN g % 3 = 0 THEN 'budget_exceeded' ELSE 'review_required' END,"
            + " '[]'::jsonb, '[]'::jsonb, '{}'::jsonb,"
            + " 'UNASSIGNED',"
            + " now() - g * interval '1 second', now() FROM generate_series(1, ?) AS g",
        REVIEW_ITEMS);
    jdbc.execute("ANALYZE review_items");

    List<Duration> all = measure("/api/v1/quality/review-items?page=0&size=20");
    List<Duration> filtered =
        measure("/api/v1/quality/review-items?status=UNASSIGNED&reason=budget_exceeded&size=20");

    assertThat(p95(all)).as("list p95").isLessThanOrEqualTo(Duration.ofMillis(300));
    assertThat(p95(filtered)).as("filtered list p95").isLessThanOrEqualTo(Duration.ofMillis(300));
  }

  @Test
  @DisplayName("[SPEC-029 NFR-4] 기록 5,000건인 영상의 리포트 p95가 500ms 이하다")
  void sourceReportP95() throws Exception {
    UUID source = UUID.randomUUID();
    Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
    List<StageTiming> timings = new ArrayList<>();
    for (int i = 0; i < TIMINGS; i++) {
      timings.add(
          StageFixtures.timing(
              source, STAGES[i % STAGES.length], start.plusMillis(i), 10 + i % 997, "ok"));
    }
    ingest.ingest(new StageTimingBatch(timings));

    List<Duration> report = measure("/api/v1/quality/stage-reports/sources/" + source);

    assertThat(p95(report)).as("source report p95").isLessThanOrEqualTo(Duration.ofMillis(500));
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
