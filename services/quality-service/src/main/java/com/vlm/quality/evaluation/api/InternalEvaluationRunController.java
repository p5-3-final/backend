package com.vlm.quality.evaluation.api;

import com.vlm.quality.evaluation.application.EvaluationRunService;
import com.vlm.quality.evaluation.application.SubmitRunCommand;
import com.vlm.quality.evaluation.application.SubmittedRun;
import com.vlm.quality.stage.application.StageGateCommand;
import com.vlm.quality.stage.application.StageGateReport;
import com.vlm.quality.stage.application.StageGateService;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.InternalApi;
import vlm.contracts.quality.model.EvaluationRun;
import vlm.contracts.quality.model.StageGateRequest;
import vlm.contracts.quality.model.StageGateResult;
import vlm.contracts.quality.model.StageGateResultStagesInner;
import vlm.contracts.quality.model.SubmitEvaluationRunRequest;

/** 평가 잡 전용 내부 API(계약 tag internal). 호출자 검사는 {@code InternalCallerInterceptor}가 먼저 한다(R-17). */
@RestController
@RequiredArgsConstructor
class InternalEvaluationRunController implements InternalApi {

  private final EvaluationRunService runs;
  private final StageGateService stageGates;

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

  // 생성된 InternalApi 하나에 두 작업이 있어 컨트롤러를 나눌 수 없다. 계산은 stage 기능이 한다(SPEC-030 R-16).
  @Override
  public ResponseEntity<StageGateResult> evaluateStageGate(
      String xCaller, StageGateRequest request) {
    StageGateReport gate =
        stageGates.evaluate(
            new StageGateCommand(
                List.copyOf(request.getBaselineSourceIds()),
                List.copyOf(request.getCandidateSourceIds()),
                stageIds(request),
                request.getMaxRegressionRatio().doubleValue(),
                request.getMinSamples()));
    return ResponseEntity.ok(stageGateResult(gate));
  }

  // 계약 stageId가 상수 oneOf라 생성 모델이 List<Object>다. 계약에 없는 단계 ID는 기록이 없어 skipped로 돌아간다.
  private static List<String> stageIds(StageGateRequest request) {
    List<Object> ids = request.getStageIds();
    return ids == null ? List.of() : ids.stream().map(String::valueOf).toList();
  }

  private static StageGateResult stageGateResult(StageGateReport gate) {
    return new StageGateResult(
        gate.passed(),
        BigDecimal.valueOf(gate.maxRegressionRatio()),
        gate.stages().stream().map(InternalEvaluationRunController::stage).toList());
  }

  private static StageGateResultStagesInner stage(StageGateReport.Stage stage) {
    return new StageGateResultStagesInner(
        stage.stageId(),
        decimal(stage.baselineP95Ms()),
        decimal(stage.candidateP95Ms()),
        decimal(stage.ratio()),
        stage.baselineCount(),
        stage.candidateCount(),
        StageGateResultStagesInner.StatusEnum.fromValue(stage.status()));
  }

  private static @Nullable BigDecimal decimal(@Nullable Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
