package com.vlm.quality.evaluation.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 평가 실행 저장소. */
public interface EvaluationRunRepository {

  /** 같은 runId가 없을 때만 넣는다(R-17). 넣었으면 true. */
  boolean insertIfAbsent(EvaluationRun run);

  Optional<EvaluationRun> findByRunId(UUID runId);

  /** 받은 시각 내림차순(R-20). goldenSetId가 null이면 전체다. */
  List<EvaluationRun> list(@Nullable UUID goldenSetId, int page, int size);

  long count(@Nullable UUID goldenSetId);
}
