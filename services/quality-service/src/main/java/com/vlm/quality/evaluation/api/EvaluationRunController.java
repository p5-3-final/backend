package com.vlm.quality.evaluation.api;

import com.vlm.quality.evaluation.application.BaselineService;
import com.vlm.quality.evaluation.application.EvaluationRunService;
import com.vlm.quality.shared.api.PageMetas;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.EvaluationApi;
import vlm.contracts.quality.model.EvaluationRun;
import vlm.contracts.quality.model.GoldenSet;
import vlm.contracts.quality.model.ListEvaluationRuns200Response;
import vlm.contracts.quality.model.SetBaselineRequest;

/** 평가 실행 조회와 기준 실행 지정(계약 tag evaluation, SPEC-029 R-19, R-20). */
@RestController
@RequiredArgsConstructor
class EvaluationRunController implements EvaluationApi {

  private final EvaluationRunService runs;
  private final BaselineService baselines;

  @Override
  public ResponseEntity<ListEvaluationRuns200Response> listEvaluationRuns(
      UUID xUserId, String xUserRoles, @Nullable UUID goldenSetId, Integer page, Integer size) {
    var found = runs.list(goldenSetId, page, size);
    return ResponseEntity.ok(
        new ListEvaluationRuns200Response(
            found.items().stream().map(EvaluationRunResponses::of).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  @Override
  public ResponseEntity<EvaluationRun> getEvaluationRun(
      UUID xUserId, String xUserRoles, UUID runId) {
    return ResponseEntity.ok(EvaluationRunResponses.of(runs.get(runId)));
  }

  @Override
  public ResponseEntity<GoldenSet> setGoldenSetBaseline(
      UUID xUserId, String xUserRoles, UUID setId, SetBaselineRequest request) {
    return ResponseEntity.ok(
        EvaluationRunResponses.goldenSet(baselines.setBaseline(setId, request.getRunId())));
  }
}
