package com.vlm.quality.evaluation.application;

import com.vlm.quality.evaluation.domain.EvaluationRun;

/**
 * 제출 결과.
 *
 * @param created 새로 저장했으면 true, 같은 runId가 이미 있어 처음 실행을 돌려주면 false(R-17)
 */
public record SubmittedRun(EvaluationRun run, boolean created) {}
