package com.vlm.quality.evaluation.api;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.EvaluationFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
class SubmitEvaluationRunApiTest {

  @Autowired MockMvc mvc;
  @Autowired EvaluationFixtures evaluations;

  @Test
  @DisplayName("[SPEC-029 R-17] 확정 버전이 있으면 201이고, 같은 runId를 다시 내면 처음 실행을 200으로 돌려준다")
  void submitsIdempotently() throws Exception {
    UUID setId = evaluations.frozenSet("submit-set");
    UUID runId = UUID.randomUUID();

    evaluations
        .submit(EvaluationFixtures.run(runId, setId, 1, "0.74"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.runId").value(runId.toString()))
        .andExpect(jsonPath("$.gate.passed").isEmpty())
        .andExpect(jsonPath("$.gate.tolerance").value(0.02))
        .andExpect(jsonPath("$.receivedAt").isNotEmpty());
    evaluations
        .submit(EvaluationFixtures.run(runId, setId, 1, "0.99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.metrics[0].value").value(0.74));
  }

  @Test
  @DisplayName("[SPEC-029 R-17] 없는 Golden Set 버전이면 404 QUALITY_GOLDEN_SET_NOT_FOUND다")
  void rejectsUnknownVersion() throws Exception {
    UUID setId = evaluations.frozenSet("missing-version-set");

    evaluations
        .submit(EvaluationFixtures.run(UUID.randomUUID(), setId, 2, "0.74"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-029 R-17] 허용 목록 밖 X-Caller는 403 QUALITY_CALLER_NOT_ALLOWED다")
  void rejectsUnknownCaller() throws Exception {
    mvc.perform(
            MockMvcRequestBuilders.post(EvaluationFixtures.SUBMIT)
                .header("X-Caller", "someone-else")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EvaluationFixtures.run(UUID.randomUUID(), UUID.randomUUID(), 1, "0.5")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUALITY_CALLER_NOT_ALLOWED"));
    mvc.perform(
            MockMvcRequestBuilders.post(EvaluationFixtures.SUBMIT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EvaluationFixtures.run(UUID.randomUUID(), UUID.randomUUID(), 1, "0.5")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-18] 기준 0.78·허용 0.02에서 0.74를 내면 Gate가 passed=false이고 실패에 judge_precision이 있다")
  void attachesFailingGate() throws Exception {
    UUID setId = evaluations.frozenSet("gate-set");
    UUID baseline = evaluations.baseline(setId, "0.78");

    evaluations
        .submit(EvaluationFixtures.run(UUID.randomUUID(), setId, 1, "0.74"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.gate.baselineRunId").value(baseline.toString()))
        .andExpect(jsonPath("$.gate.passed").value(false))
        .andExpect(jsonPath("$.gate.failures[0].name").value("judge_precision"))
        .andExpect(jsonPath("$.gate.failures[0].baseline").value(0.78))
        .andExpect(jsonPath("$.gate.failures[0].value").value(0.74));
    evaluations
        .submit(EvaluationFixtures.run(UUID.randomUUID(), setId, 1, "0.77"))
        .andExpect(jsonPath("$.gate.passed").value(true));
  }
}
