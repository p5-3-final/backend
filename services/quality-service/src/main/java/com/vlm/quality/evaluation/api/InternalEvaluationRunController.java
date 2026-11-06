package com.vlm.quality.evaluation.api;

import com.vlm.quality.evaluation.application.EvaluationRunService;
import com.vlm.quality.evaluation.application.SubmitRunCommand;
import com.vlm.quality.evaluation.application.SubmittedRun;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.InternalApi;
import vlm.contracts.quality.model.EvaluationRun;
import vlm.contracts.quality.model.StageGateRequest;
import vlm.contracts.quality.model.StageGateResult;
import vlm.contracts.quality.model.SubmitEvaluationRunRequest;

/** 평가 잡 전용 내부 API(계약 tag internal). 호출자 검사는 {@code InternalCallerInterceptor}가 먼저 한다(R-17). */
@RestController
@RequiredArgsConstructor
class InternalEvaluationRunController implements InternalApi {

  private final EvaluationRunService runs;

  @Override
  public ResponseEntity<EvaluationRun> submitEvaluationRun(
      String xCaller, SubmitEvaluationRunRequest request) {
    SubmittedRun submitted =
        runs.submit(
            new SubmitRunCommand(
                request.getRunId(),
                request.getGoldenSetId(),
                request.getGoldenSetVersion(),
                request.getKind().getValue(),
                EvaluationRunResponses.versions(request),
                EvaluationRunResponses.metrics(request),
                request.getStartedAt().toInstant(),
                request.getFinishedAt().toInstant(),
                request.getTrigger().getValue(),
                request.getCommitSha()));
    return ResponseEntity.status(submitted.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(EvaluationRunResponses.of(submitted.run()));
  }

  // 처리 단계 성능 Gate는 SPEC-030 범위다. 그 plan에서 구현할 때까지 501을 돌려준다.
  @Override
  public ResponseEntity<StageGateResult> evaluateStageGate(
      String xCaller, StageGateRequest stageGateRequest) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
  }
}
