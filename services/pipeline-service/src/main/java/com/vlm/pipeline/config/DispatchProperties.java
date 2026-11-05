package com.vlm.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 같은 DB의 모든 배정 서버는 동일한 상한과 시작 기한을 사용한다. */
@ConfigurationProperties("pipeline.dispatch")
public record DispatchProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("2") int userLimit,
    @DefaultValue("1") int sourceLimit,
    @DefaultValue("4") int globalLimit,
    @DefaultValue("300000") long startWindowMs,
    @DefaultValue("1000") long intervalMs,
    @DefaultValue("100") int batchSize) {
  public DispatchProperties {
    if (sourceLimit <= 0
        || userLimit < sourceLimit
        || globalLimit < userLimit
        || startWindowMs <= 0
        || startWindowMs > 86400000
        || intervalMs <= 0
        || batchSize <= 0
        || batchSize > 1000) throw new IllegalArgumentException("Invalid dispatch limits");
  }
}
