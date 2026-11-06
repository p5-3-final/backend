package com.vlm.quality.evaluation.application;

import com.vlm.quality.evaluation.domain.Metric;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 평가 잡이 낸 실행(계약 SubmitEvaluationRunRequest). */
public record SubmitRunCommand(
    UUID runId,
    UUID goldenSetId,
    int goldenSetVersion,
    String kind,
    Map<String, @Nullable String> versions,
    List<Metric> metrics,
    Instant startedAt,
    Instant finishedAt,
    String trigger,
    @Nullable String commitSha) {

  public SubmitRunCommand {
    versions = Collections.unmodifiableMap(new LinkedHashMap<>(versions));
    metrics = List.copyOf(metrics);
  }
}
