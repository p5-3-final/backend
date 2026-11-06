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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class BaselineApiTest {

  @Autowired MockMvc mvc;
  @Autowired EvaluationFixtures evaluations;

  @Test
  @DisplayName("[SPEC-029 R-19] 같은 Golden Set의 실행이면 200이고 baselineRunId가 바뀐다")
  void setsBaseline() throws Exception {
    UUID setId = evaluations.frozenSet("baseline-set");
    UUID runId = UUID.randomUUID();
    evaluations
        .submit(EvaluationFixtures.run(runId, setId, 1, "0.8"))
        .andExpect(status().isCreated());

    setBaseline(setId, runId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baselineRunId").value(runId.toString()))
        .andExpect(jsonPath("$.setId").value(setId.toString()));
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-19] 다른 Golden Set의 실행이면 409 QUALITY_RUN_SET_MISMATCH, 없는 실행은 404 QUALITY_RUN_NOT_FOUND다")
  void rejectsMismatchAndMissing() throws Exception {
    UUID setId = evaluations.frozenSet("mine-set");
    UUID other = evaluations.frozenSet("other-set");
    UUID otherRun = UUID.randomUUID();
    evaluations
        .submit(EvaluationFixtures.run(otherRun, other, 1, "0.8"))
        .andExpect(status().isCreated());

    setBaseline(setId, otherRun)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_RUN_SET_MISMATCH"));
    setBaseline(setId, UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_RUN_NOT_FOUND"));
    setBaseline(UUID.randomUUID(), otherRun)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_NOT_FOUND"));
  }

  private ResultActions setBaseline(UUID setId, UUID runId) throws Exception {
    return mvc.perform(
        operator(
                HttpMethod.PUT,
                UUID.randomUUID(),
                "/api/v1/quality/golden-sets/{setId}/baseline",
                setId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"runId\":\"" + runId + "\"}"));
  }
}
