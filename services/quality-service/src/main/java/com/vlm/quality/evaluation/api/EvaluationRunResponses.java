package com.vlm.quality.evaluation.api;

import com.vlm.quality.evaluation.domain.EvaluationRun;
import com.vlm.quality.evaluation.domain.GateFailure;
import com.vlm.quality.evaluation.domain.Metric;
import com.vlm.quality.golden.application.GoldenSetView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import vlm.contracts.quality.model.GateResult;
import vlm.contracts.quality.model.GateResultFailuresInner;
import vlm.contracts.quality.model.GoldenSet;
import vlm.contracts.quality.model.RunVersions;
import vlm.contracts.quality.model.SubmitEvaluationRunRequest;

/** 평가 실행을 계약 모델과 서로 옮긴다. */
final class EvaluationRunResponses {

  private EvaluationRunResponses() {}

  static vlm.contracts.quality.model.EvaluationRun of(EvaluationRun run) {
    Map<String, @Nullable String> versions = run.versions();
    return new vlm.contracts.quality.model.EvaluationRun(
        run.runId(),
        run.goldenSetId(),
        run.goldenSetVersion(),
        vlm.contracts.quality.model.EvaluationRun.KindEnum.fromValue(run.kind()),
        new RunVersions(
            versions.get("taxonomy"),
            versions.get("detectorProfile"),
            versions.get("modelId"),
            versions.get("promptVersion"),
            versions.get("embeddingModel")),
        run.metrics().stream().map(EvaluationRunResponses::metric).toList(),
        offset(run.startedAt()),
        offset(run.finishedAt()),
        vlm.contracts.quality.model.EvaluationRun.TriggerEnum.fromValue(run.trigger()),
        run.commitSha(),
        new GateResult(
            run.gate().baselineRunId(),
            run.gate().passed(),
            run.gate().tolerance(),
            run.gate().failures().stream().map(EvaluationRunResponses::failure).toList()),
        offset(run.receivedAt()));
  }

  static Map<String, @Nullable String> versions(SubmitEvaluationRunRequest request) {
    RunVersions versions = request.getVersions();
    Map<String, @Nullable String> values = new LinkedHashMap<>();
    values.put("taxonomy", versions.getTaxonomy());
    values.put("detectorProfile", versions.getDetectorProfile());
    values.put("modelId", versions.getModelId());
    values.put("promptVersion", versions.getPromptVersion());
    values.put("embeddingModel", versions.getEmbeddingModel());
    return values;
  }

  static List<Metric> metrics(SubmitEvaluationRunRequest request) {
    return request.getMetrics().stream()
        .map(
            m ->
                new Metric(
                    m.getName(),
                    m.getEventType() == null ? null : m.getEventType().getValue(),
                    m.getValue(),
                    Boolean.TRUE.equals(m.getHigherIsBetter())))
        .toList();
  }

  static GoldenSet goldenSet(GoldenSetView set) {
    return new GoldenSet(
        set.setId(),
        set.name(),
        GoldenSet.SplitEnum.fromValue(set.split()),
        set.description(),
        set.draftEntryCount(),
        set.latestVersion(),
        set.baselineRunId(),
        offset(set.createdAt()),
        GoldenSet.OriginEnum.fromValue(set.origin()));
  }

  private static vlm.contracts.quality.model.Metric metric(Metric metric) {
    return new vlm.contracts.quality.model.Metric(
        metric.name(),
        metric.eventType() == null
            ? null
            : vlm.contracts.quality.model.Metric.EventTypeEnum.fromValue(metric.eventType()),
        metric.value(),
        metric.higherIsBetter());
  }

  private static GateResultFailuresInner failure(GateFailure failure) {
    return new GateResultFailuresInner(
        failure.name(), failure.eventType(), failure.baseline(), failure.value());
  }

  private static OffsetDateTime offset(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
