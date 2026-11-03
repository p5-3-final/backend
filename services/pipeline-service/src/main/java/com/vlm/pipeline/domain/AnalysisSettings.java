package com.vlm.pipeline.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** backend가 버전 관리하는 불변 계획·추론 설정. 실제 추론 호출을 뜻하지 않는다. */
public record AnalysisSettings(String version, String mode, String inferenceProfileVersion) {
  public AnalysisSettings {
    Objects.requireNonNull(version);
    Objects.requireNonNull(mode);
    Objects.requireNonNull(inferenceProfileVersion);
    if (!version.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")
        || !inferenceProfileVersion.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")
        || !(mode.equals("production") || mode.equals("local")))
      throw new IllegalArgumentException("Invalid analysis settings");
  }

  public PlanInput plan(UUID jobId, NormalizedVideo event) {
    BigDecimal seconds = BigDecimal.valueOf(event.durationMs(), 3);
    return mode.equals("local")
        ? PlanInput.local(jobId, event.sourceId(), seconds)
        : PlanInput.production(jobId, event.sourceId(), seconds);
  }

  public String definition() {
    return "{\"mode\":\"%s\",\"inferenceProfileVersion\":\"%s\",\"planningRules\":\"spec004-v1\"}"
        .formatted(mode, inferenceProfileVersion);
  }
}
