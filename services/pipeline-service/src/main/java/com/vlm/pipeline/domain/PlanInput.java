package com.vlm.pipeline.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** 내부 계획 입력. 시간은 정규화 영상의 PTS 초이며 외부 메시지 형식을 정의하지 않는다. */
public record PlanInput(
    UUID planId,
    UUID sourceId,
    BigDecimal duration,
    String profile,
    BigDecimal chunkLength,
    BigDecimal overlap) {
  public PlanInput {
    Objects.requireNonNull(planId);
    Objects.requireNonNull(sourceId);
    Objects.requireNonNull(profile);
    duration = duration.stripTrailingZeros();
    chunkLength = chunkLength.stripTrailingZeros();
    overlap = overlap.stripTrailingZeros();
    if (duration.signum() <= 0
        || chunkLength.signum() <= 0
        || overlap.signum() < 0
        || profile.isBlank()
        || profile.length() > 100) throw new IllegalArgumentException("Invalid planning input");
  }

  public static PlanInput production(UUID plan, UUID source, BigDecimal duration) {
    int seconds =
        duration.compareTo(BigDecimal.valueOf(600)) < 0
            ? 60
            : duration.compareTo(BigDecimal.valueOf(7200)) > 0 ? 180 : 120;
    return new PlanInput(
        plan, source, duration, "production", BigDecimal.valueOf(seconds), BigDecimal.valueOf(5));
  }

  public static PlanInput local(UUID plan, UUID source, BigDecimal duration) {
    return new PlanInput(
        plan, source, duration, "local", BigDecimal.valueOf(60), BigDecimal.valueOf(5));
  }
}
