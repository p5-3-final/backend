package com.vlm.quality.stage.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 처리 단계 측정 설정.
 *
 * @param retention 원본 보존 기간(overview 보존 표 90일, R-25)
 */
@ConfigurationProperties("vlm.quality.stage")
public record StageProperties(Duration retention) {}
