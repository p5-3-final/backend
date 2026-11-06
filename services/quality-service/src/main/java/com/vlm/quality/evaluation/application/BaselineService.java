package com.vlm.quality.evaluation.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.evaluation.domain.EvaluationRun;
import com.vlm.quality.golden.application.GoldenSetService;
import com.vlm.quality.golden.application.GoldenSetView;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 실행 지정(SPEC-029 R-19).
 *
 * <p>evaluation이 golden을 부르고 golden은 evaluation을 모르게 해 두 기능 사이 순환을 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
public class BaselineService {

  private final GoldenSetService goldenSets;
  private final EvaluationRunService runs;

  @Transactional
  public GoldenSetView setBaseline(UUID setId, UUID runId) {
    goldenSets.view(setId);
    EvaluationRun run = runs.get(runId);
    if (!run.goldenSetId().equals(setId)) {
      throw new BusinessException(QualityErrorCode.RUN_SET_MISMATCH);
    }
    return goldenSets.setBaseline(setId, runId);
  }
}
