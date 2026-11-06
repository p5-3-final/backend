package com.vlm.quality.evaluation.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.EvaluationFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class EvaluationRunQueryApiTest {

  private static final UUID OPERATOR = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired EvaluationFixtures evaluations;

  @Test
  @DisplayName("[SPEC-029 R-20] 실행 목록은 받은 시각 내림차순이고 Golden Set으로 거른다")
  void listsNewestFirst() throws Exception {
    UUID setId = evaluations.frozenSet("list-set");
    UUID other = evaluations.frozenSet("list-other");
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    evaluations
        .submit(EvaluationFixtures.run(first, setId, 1, "0.7"))
        .andExpect(status().isCreated());
    evaluations
        .submit(EvaluationFixtures.run(second, setId, 1, "0.8"))
        .andExpect(status().isCreated());
    evaluations
        .submit(EvaluationFixtures.run(UUID.randomUUID(), other, 1, "0.8"))
        .andExpect(status().isCreated());

    mvc.perform(
            operator(
                HttpMethod.GET, OPERATOR, "/api/v1/quality/evaluation-runs?goldenSetId=" + setId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].runId").value(second.toString()))
        .andExpect(jsonPath("$.items[1].runId").value(first.toString()));
    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/evaluation-runs"))
        .andExpect(jsonPath("$.page.totalElements").value(3));
  }

  @Test
  @DisplayName("[SPEC-029 R-20] 상세는 지표와 Gate 결과를 담고, 없으면 404 QUALITY_RUN_NOT_FOUND다")
  void getsRun() throws Exception {
    UUID setId = evaluations.frozenSet("detail-set");
    UUID runId = UUID.randomUUID();
    evaluations
        .submit(EvaluationFixtures.run(runId, setId, 1, "0.7"))
        .andExpect(status().isCreated());

    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/evaluation-runs/{id}", runId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics[0].name").value("judge_precision"))
        .andExpect(jsonPath("$.versions.modelId").value("gpt-6.1-terra"))
        .andExpect(jsonPath("$.gate.failures").isArray());
    mvc.perform(
            operator(
                HttpMethod.GET,
                OPERATOR,
                "/api/v1/quality/evaluation-runs/{id}",
                UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_RUN_NOT_FOUND"));
  }
}
