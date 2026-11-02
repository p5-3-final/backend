package com.vlm.gateway.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 교차 출처 허용 설정(SPEC-001 R-21).
 *
 * @param allowedOrigins 허용할 frontend 주소 목록
 */
@ConfigurationProperties("vlm.gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins) {}
