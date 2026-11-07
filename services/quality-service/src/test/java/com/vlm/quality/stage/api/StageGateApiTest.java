package com.vlm.quality.stage.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class StageGateApiTest {

  private static final String GATE = "/internal/v1/quality/stage-gates";
  private static final String TRANSCODE = "normalize.transcode";
  private static final String HANDLE = "normalize.handle";
  private static final String PROBE = "normalize.probe";
  // percentile_cont 선형 보간과 부동소수 합산 차이를 허용한다.
  private static final double EPSILON = 1e-6;

  @Autowired MockMvc mvc;
  @Autowired StageTimingIngestService ingest;
  @Autowired JdbcTemplate jdbc;

  private final UUID baseline = UUID.randomUUID();
  private final UUID candidate = UUID.randomUUID();
  private final Instant recent = Instant.now().minus(Duration.ofHours(1));

  @Test
  @DisplayName(
      "[SPEC-030 R-16] 기준 p95 3,400ms → 후보 4,300ms인 단계는 기본 0.2를 넘어 failed이고 passed는 false다")
  void failsOnRegression() throws Exception {
    record(baseline, TRANSCODE, 3400, 6);
    record(candidate, TRANSCODE, 4300, 6);

    gate("{\"baselineSourceIds\":[\""
            + baseline
            + "\"],\"candidateSourceIds\":[\""
            + candidate
            + "\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(false))
        .andExpect(jsonPath("$.maxRegressionRatio").value(0.2))
        .andExpect(jsonPath("$.stages", hasSize(1)))
        .andExpect(jsonPath("$.stages[0].stageId").value(TRANSCODE))
        .andExpect(jsonPath("$.stages[0].baselineP95Ms").value(closeTo(3400.0, EPSILON)))
        .andExpect(jsonPath("$.stages[0].candidateP95Ms").value(closeTo(4300.0, EPSILON)))
        .andExpect(jsonPath("$.stages[0].ratio").value(closeTo(4300.0 / 3400.0, EPSILON)))
        .andExpect(jsonPath("$.stages[0].baselineCount").value(6))
        .andExpect(jsonPath("$.stages[0].candidateCount").value(6))
        .andExpect(jsonPath("$.stages[0].status").value("failed"));
  }

  @Test
  @DisplayName("[SPEC-030 R-16] stageIds가 없으면 양쪽에 기록이 있는 단계만 계산하고 음수 기록은 백분위와 개수에서 뺀다")
  void comparesCommonStages() throws Exception {
    record(baseline, TRANSCODE, 1000, 5);
    record(candidate, TRANSCODE, 1100, 5);
    record(candidate, TRANSCODE, -20, 3);
    record(baseline, HANDLE, 500, 5);

    gate("{\"baselineSourceIds\":[\""
            + baseline
            + "\"],\"candidateSourceIds\":[\""
            + candidate
            + "\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true))
        .andExpect(jsonPath("$.stages[*].stageId", contains(TRANSCODE)))
        .andExpect(jsonPath("$.stages[0].candidateCount").value(5))
        .andExpect(jsonPath("$.stages[0].status").value("passed"));
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-16, R-17] stageIds를 주면 그 단계만이고, 한쪽 기록이 minSamples보다 적은 단계는 skipped라 Gate에 영향이 없다")
  void onlyRequestedStages() throws Exception {
    record(baseline, TRANSCODE, 1000, 3);
    record(candidate, TRANSCODE, 9000, 3);
    record(baseline, HANDLE, 1000, 3);
    record(candidate, HANDLE, 1000, 3);

    gate("{\"baselineSourceIds\":[\""
            + baseline
            + "\"],\"candidateSourceIds\":[\""
            + candidate
            + "\"],\"stageIds\":[\""
            + TRANSCODE
            + "\",\""
            + PROBE
            + "\"],\"maxRegressionRatio\":0.5,\"minSamples\":4}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true))
        .andExpect(jsonPath("$.maxRegressionRatio").value(0.5))
        .andExpect(jsonPath("$.stages[*].stageId", contains(PROBE, TRANSCODE)))
        .andExpect(jsonPath("$.stages[0].baselineCount").value(0))
        .andExpect(jsonPath("$.stages[0].baselineP95Ms").value(nullValue()))
        .andExpect(jsonPath("$.stages[0].ratio").value(nullValue()))
        .andExpect(jsonPath("$.stages[0].status").value("skipped"))
        .andExpect(jsonPath("$.stages[1].status").value("skipped"));
  }

  @Test
  @DisplayName("[SPEC-030 R-18] 계산 결과를 저장하지 않아 계산 뒤에도 모든 테이블의 행 수가 같다")
  void storesNothing() throws Exception {
    record(baseline, TRANSCODE, 1000, 5);
    record(candidate, TRANSCODE, 1000, 5);
    Map<String, Integer> before = rowCounts();

    gate("{\"baselineSourceIds\":[\""
            + baseline
            + "\"],\"candidateSourceIds\":[\""
            + candidate
            + "\"]}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passed").value(true));

    assertThat(rowCounts()).isEqualTo(before);
  }

  @Test
  @DisplayName("[SPEC-030 R-16] 허용 목록 밖 X-Caller는 403 QUALITY_CALLER_NOT_ALLOWED다")
  void rejectsUnknownCaller() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post(GATE)
                .header("X-Caller", "someone-else")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"baselineSourceIds\":[\""
                        + baseline
                        + "\"],\"candidateSourceIds\":[\""
                        + candidate
                        + "\"]}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUALITY_CALLER_NOT_ALLOWED"));
  }

  private void record(UUID sourceId, String stageId, double durationMs, int count) {
    for (int i = 0; i < count; i++) {
      ingest.ingest(
          StageFixtures.batch(StageFixtures.timing(sourceId, stageId, recent, durationMs, "ok")));
    }
  }

  private ResultActions gate(String body) throws Exception {
    return mvc.perform(
        MockMvcRequestBuilders.post(GATE)
            .header("X-Caller", "quality-evals")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private Map<String, Integer> rowCounts() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = current_schema()"
                + " AND tablename NOT LIKE 'flyway%'",
            String.class);
    return tables.stream()
        .collect(
            Collectors.toMap(
                table -> table,
                table ->
                    jdbc.queryForObject("SELECT count(*) FROM \"" + table + "\"", Integer.class)));
  }
}
