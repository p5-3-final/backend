package com.vlm.quality.evaluation.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 평가 잡이 낸 실행(계약 EvaluationRun, SPEC-029 R-17~R-20).
 *
 * @param versions 재현에 필요한 버전(계약 RunVersions). 쓰지 않은 키는 값이 null이다
 * @param gate 받을 때 계산한 Gate 결과
 */
public record EvaluationRun(
    UUID runId,
    UUID goldenSetId,
    int goldenSetVersion,
    String kind,
    Map<String, @Nullable String> versions,
    List<Metric> metrics,
    Instant startedAt,
    Instant finishedAt,
    String trigger,
    @Nullable String commitSha,
    GateResult gate,
    Instant receivedAt) {

  public EvaluationRun {
    versions = Collections.unmodifiableMap(new LinkedHashMap<>(versions));
    metrics = List.copyOf(metrics);
  }
}
