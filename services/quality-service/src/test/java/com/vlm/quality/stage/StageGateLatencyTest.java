package com.vlm.quality.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.StageTimingBatch;
import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.stage.domain.StageTiming;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class StageGateLatencyTest {

  private static final int SOURCES = 200;
  // 영상 하나가 거치는 단계와 단계당 기록 수. 영상 400개 × 5단계 × 10건 = 20,000건이다.
  private static final String[] STAGES = {
    "normalize.queue_wait",
    "normalize.handle",
    "normalize.transcode",
    "chunk-processor.handle",
    "frame-encoder.handle"
  };
  private static final int RECORDS_PER_STAGE = 10;
  private static final int WARM_UP = 3;
  private static final int SAMPLES = 20;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired StageTimingIngestService ingest;

  @Test
  @DisplayName("[SPEC-030 NFR-3] 영상 200개 기준·후보 성능 Gate p95가 2초 이하다")
  void gateP95() throws Exception {
    List<UUID> baseline = load(1000);
    List<UUID> candidate = load(1100);
    jdbc.execute("ANALYZE stage_timings");
    String body =
        "{\"baselineSourceIds\":"
            + array(baseline)
            + ",\"candidateSourceIds\":"
            + array(candidate)
            + "}";

    List<Duration> samples = new ArrayList<>();
    for (int i = 0; i < WARM_UP + SAMPLES; i++) {
      long started = System.nanoTime();
      mvc.perform(
              MockMvcRequestBuilders.post("/internal/v1/quality/stage-gates")
                  .header("X-Caller", "quality-eval")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk());
      if (i >= WARM_UP) {
        samples.add(Duration.ofNanos(System.nanoTime() - started));
      }
    }

    List<Duration> sorted = samples.stream().sorted().toList();
    Duration p95 = sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1);
    assertThat(p95).as("stage gate p95").isLessThanOrEqualTo(Duration.ofSeconds(2));
  }

  private List<UUID> load(double baseMs) {
    Instant at = Instant.now().minus(Duration.ofHours(1));
    List<UUID> sources = new ArrayList<>();
    List<StageTiming> timings = new ArrayList<>();
    for (int s = 0; s < SOURCES; s++) {
      UUID source = UUID.randomUUID();
      sources.add(source);
      for (String stage : STAGES) {
        for (int r = 0; r < RECORDS_PER_STAGE; r++) {
          timings.add(StageFixtures.timing(source, stage, at.plusMillis(r), baseMs + r, "ok"));
        }
      }
    }
    ingest.ingest(new StageTimingBatch(timings));
    return sources;
  }

  private static String array(List<UUID> ids) {
    return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
  }
}
