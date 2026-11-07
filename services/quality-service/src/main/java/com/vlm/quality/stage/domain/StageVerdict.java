package com.vlm.quality.stage.domain;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** 한 단계의 Gate 판정(계약 StageGateResult.stages[]). */
public record StageVerdict(StageSample sample, @Nullable Double ratio, Status status) {

  /** 계약 status. */
  public enum Status {
    PASSED,
    FAILED,
    SKIPPED;

    public String wire() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
