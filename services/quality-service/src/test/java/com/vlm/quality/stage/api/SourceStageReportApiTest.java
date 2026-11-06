package com.vlm.quality.stage.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class SourceStageReportApiTest {

  // percentile_cont 보간은 부동소수점이라 CPU(FMA 사용 여부)에 따라 마지막 자리가 다르다.
  private static final double EPSILON = 1e-6;
  private static final String REPORT = "/api/v1/quality/stage-reports/sources/{sourceId}";

  @Autowired MockMvc mvc;
  @Autowired StageTimingIngestService ingest;

  @Test
  @DisplayName("[SPEC-029 R-22] 경로별 개수·합계·p50·p95·오류 수를 percentile_cont로 계산하고 음수는 개수에만 넣는다")
  void reportsPerPath() throws Exception {
    UUID source = UUID.randomUUID();
    Instant now = Instant.now();
    // 100, 200, ..., 1000(10건)과 음수 1건. 선형 보간 p50 = 550, p95 = 955
    for (int i = 1; i <= 10; i++) {
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.timing(
                  source, "analyzer.judge", now, i * 100.0, i == 3 ? "error" : "ok")));
    }
    ingest.ingest(
        StageFixtures.batch(StageFixtures.timing(source, "analyzer.judge", now, -50, "ok")));
    ingest.ingest(
        StageFixtures.batch(
            StageFixtures.timing(source, "event-analyzer.queue_wait", now, -5, "ok")));

    mvc.perform(operator(HttpMethod.GET, UUID.randomUUID(), REPORT, source))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceId").value(source.toString()))
        .andExpect(jsonPath("$.recordCount").value(12))
        .andExpect(jsonPath("$.stages[?(@.stageId=='analyzer.judge')].count").value(11))
        .andExpect(jsonPath("$.stages[?(@.stageId=='analyzer.judge')].totalMs").value(5500.0))
        .andExpect(
            jsonPath(
                "$.stages[?(@.stageId=='analyzer.judge')].p50Ms",
                contains(closeTo(550.0, EPSILON))))
        .andExpect(
            jsonPath(
                "$.stages[?(@.stageId=='analyzer.judge')].p95Ms",
                contains(closeTo(955.0, EPSILON))))
        .andExpect(jsonPath("$.stages[?(@.stageId=='analyzer.judge')].errorCount").value(1))
        .andExpect(
            jsonPath("$.stages[?(@.stageId=='event-analyzer.queue_wait')].totalMs").value(0.0))
        .andExpect(
            jsonPath("$.stages[?(@.stageId=='event-analyzer.queue_wait')].p50Ms").value(0.0));
  }

  @Test
  @DisplayName("[SPEC-029 R-22] 측정 기록이 없는 영상은 404 QUALITY_SOURCE_NOT_FOUND다")
  void notFoundWithoutRecords() throws Exception {
    mvc.perform(operator(HttpMethod.GET, UUID.randomUUID(), REPORT, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_SOURCE_NOT_FOUND"));
  }
}
