package com.vlm.quality.evaluation.application;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CI Gate 설정.
 *
 * @param tolerance 허용 하락폭(절댓값, R-18 기본 0.02)
 */
@ConfigurationProperties("vlm.quality.gate")
public record GateProperties(BigDecimal tolerance) {}
