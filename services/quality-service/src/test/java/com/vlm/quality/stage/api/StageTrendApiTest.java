package com.vlm.quality.stage.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.DailyStageAggregationJob;
import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class StageTrendApiTest {

  private static final String TRENDS = "/api/v1/quality/stage-reports/trends";

  @Autowired MockMvc mvc;
  @Autowired StageTimingIngestService ingest;
  @Autowired DailyStageAggregationJob job;

  @Test
  @DisplayName("[SPEC-029 R-24] 날짜별 p50·p95를 돌려주고 버전 키·값을 주면 그 버전만, 기록 없는 날은 빠진다")
  void returnsDailyTrend() throws Exception {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate d1 = today.minusDays(3);
    LocalDate d2 = today.minusDays(1);
    for (LocalDate day : new LocalDate[] {d1, d2}) {
      var at = day.atTime(8, 0).toInstant(ZoneOffset.UTC);
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.timing(
                  UUID.randomUUID(), "analyzer.judge", at, 100, "ok", Map.of("modelId", "a"))));
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.timing(
                  UUID.randomUUID(), "analyzer.judge", at, 300, "ok", Map.of("modelId", "b"))));
      job.aggregate(day);
    }

    mvc.perform(
            operator(
                HttpMethod.GET,
                UUID.randomUUID(),
                TRENDS + "?stageId=analyzer.judge&from={f}&to={t}",
                today.minusDays(5),
                today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionKey").isEmpty())
        .andExpect(jsonPath("$.days.length()").value(2))
        .andExpect(jsonPath("$.days[0].date").value(d1.toString()))
        .andExpect(jsonPath("$.days[0].count").value(2))
        .andExpect(jsonPath("$.days[0].p50Ms").value(200.0));
    mvc.perform(
            operator(
                HttpMethod.GET,
                UUID.randomUUID(),
                TRENDS
                    + "?stageId=analyzer.judge&from={f}&to={t}&versionKey=modelId&versionValue=b",
                today.minusDays(5),
                today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versionValue").value("b"))
        .andExpect(jsonPath("$.days[1].p95Ms").value(300.0))
        .andExpect(jsonPath("$.days[1].count").value(1));
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-24] 90일 초과·from > to·versionValue만·versionKey만이면 400 QUALITY_INVALID_REQUEST다")
  void rejectsInvalidQueries() throws Exception {
    String[] queries = {
      "from=2026-01-01&to=2026-04-02",
      "from=2026-02-02&to=2026-02-01",
      "from=2026-02-01&to=2026-02-02&versionValue=x",
      "from=2026-02-01&to=2026-02-02&versionKey=modelId",
      "from=2026-02-01&to=2026-02-02&versionKey=unknown&versionValue=x"
    };
    for (String query : queries) {
      mvc.perform(
              operator(
                  HttpMethod.GET, UUID.randomUUID(), TRENDS + "?stageId=analyzer.judge&" + query))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
    }
  }
}
