package com.vlm.quality.evaluation.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.evaluation.domain.EvaluationRun;
import com.vlm.quality.evaluation.domain.EvaluationRunRepository;
import com.vlm.quality.evaluation.domain.GateCalculator;
import com.vlm.quality.evaluation.domain.GateResult;
import com.vlm.quality.evaluation.domain.Metric;
import com.vlm.quality.golden.application.GoldenSetService;
import com.vlm.quality.golden.application.GoldenSetView;
import com.vlm.quality.golden.application.Page;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 평가 실행 받기·조회(SPEC-029 R-17, R-18, R-20). */
@Service
@RequiredArgsConstructor
public class EvaluationRunService {

  private static final Logger log = LoggerFactory.getLogger(EvaluationRunService.class);

  private final EvaluationRunRepository runs;
  private final GoldenSetService goldenSets;
  private final GateProperties gate;
  private final Clock clock;

  /**
   * 실행을 받아 기준 실행과 비교한 Gate 결과를 붙여 저장한다. 같은 runId는 처음 저장한 것을 그대로 돌려준다.
   *
   * @throws BusinessException Golden Set 버전이 없으면 {@link QualityErrorCode#GOLDEN_SET_NOT_FOUND}
   */
  @Transactional
  public SubmittedRun submit(SubmitRunCommand command) {
    var existing = runs.findByRunId(command.runId());
    if (existing.isPresent()) {
      return new SubmittedRun(existing.get(), false);
    }
    if (!goldenSets.versionExists(command.goldenSetId(), command.goldenSetVersion())) {
      throw new BusinessException(QualityErrorCode.GOLDEN_SET_NOT_FOUND);
    }
    GoldenSetView set = goldenSets.view(command.goldenSetId());
    GateResult result = compare(command.metrics(), set.baselineRunId());
    var run =
        new EvaluationRun(
            command.runId(),
            command.goldenSetId(),
            command.goldenSetVersion(),
            command.kind(),
            command.versions(),
            command.metrics(),
            command.startedAt(),
            command.finishedAt(),
            command.trigger(),
            command.commitSha(),
            result,
            clock.instant());
    if (!runs.insertIfAbsent(run)) {
      // 같은 runId가 동시에 들어와 다른 요청이 먼저 저장했다. 처음 것을 돌려준다.
      return new SubmittedRun(get(command.runId()), false);
    }
    if (!result.missingInCandidate().isEmpty()) {
      // 계약 GateResult.failures[].value가 null을 허용하지 않아 기준에만 있는 지표는 failures에 넣지 못한다(계약 C-30 대기).
      log.warn(
          "Gate failed by metrics missing in candidate runId={} baselineRunId={} missing={}",
          run.runId(),
          result.baselineRunId(),
          result.missingInCandidate());
    }
    return new SubmittedRun(run, true);
  }

  @Transactional(readOnly = true)
  public Page<EvaluationRun> list(@Nullable UUID goldenSetId, int page, int size) {
    return new Page<>(runs.list(goldenSetId, page, size), runs.count(goldenSetId));
  }

  /**
   * 실행을 찾는다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#RUN_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public EvaluationRun get(UUID runId) {
    return runs.findByRunId(runId)
        .orElseThrow(() -> new BusinessException(QualityErrorCode.RUN_NOT_FOUND));
  }

  private GateResult compare(List<Metric> metrics, @Nullable UUID baselineRunId) {
    List<Metric> baseline =
        baselineRunId == null
            ? List.of()
            : runs.findByRunId(baselineRunId).map(EvaluationRun::metrics).orElse(List.of());
    return GateCalculator.compare(metrics, baselineRunId, baseline, gate.tolerance());
  }
}
