package com.vlm.quality.golden.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Golden Set 설정.
 *
 * @param freezeLockTimeout 이보다 오래된 확정 진행 표시는 Pod가 죽어 남은 것으로 보고 무시한다(R-15)
 */
@ConfigurationProperties("vlm.quality.golden")
public record GoldenProperties(Duration freezeLockTimeout) {}
