package com.vlm.pipeline.config;

import com.vlm.pipeline.domain.AnalysisSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 소비는 opt-in이다. 활성화한 운영 환경은 불변 설정 버전을 지정해야 한다. */
@ConfigurationProperties("pipeline.intake")
public record NormalizedIntakeProperties(
    @DefaultValue("") String profileVersion,
    @DefaultValue("production") String mode,
    @DefaultValue("") String inferenceProfileVersion,
    @DefaultValue("14400000") long maxDurationMs,
    @DefaultValue("1000") long retryIntervalMs,
    @DefaultValue("2") long retryAttempts) {
  public NormalizedIntakeProperties {
    if (maxDurationMs <= 0 || retryIntervalMs <= 0 || retryAttempts < 0)
      throw new IllegalArgumentException("Invalid intake limits");
  }

  public AnalysisSettings settings() {
    return new AnalysisSettings(profileVersion, mode, inferenceProfileVersion);
  }
}
