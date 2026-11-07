package com.vlm.quality.export.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내보내기 설정(SPEC-030 R-14).
 *
 * @param heartbeatTimeout 작업 표시가 이보다 오래되면 Pod가 죽은 것으로 보고 다른 작업자가 처음부터 다시 한다
 */
@ConfigurationProperties("vlm.quality.export")
public record ExportProperties(Duration heartbeatTimeout) {}
