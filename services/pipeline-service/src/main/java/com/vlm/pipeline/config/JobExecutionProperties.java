package com.vlm.pipeline.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("pipeline.execution")
public record JobExecutionProperties(
    @DefaultValue("false") boolean reconciliationEnabled,
    @DefaultValue("30000") long intervalMs,
    @DefaultValue("100") int batchSize,
    @DefaultValue("1") int maxAttempts,
    @DefaultValue List<String> retryableErrorCodes,
    @DefaultValue("1000") long retryDelayMs) {
  public JobExecutionProperties {
    retryableErrorCodes = List.copyOf(retryableErrorCodes);
    if (intervalMs < 100
        || batchSize < 1
        || batchSize > 1000
        || maxAttempts < 1
        || maxAttempts > 10
        || retryDelayMs < 0
        || retryDelayMs > 86400000
        || retryableErrorCodes.stream().anyMatch(c -> !c.matches("[A-Z][A-Z0-9_]{0,99}")))
      throw new IllegalArgumentException("Invalid execution policy");
  }
}
