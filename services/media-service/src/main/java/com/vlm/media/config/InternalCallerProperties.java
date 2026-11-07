package com.vlm.media.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내부 경로({@code /internal/**}) 호출자 허용 목록(SPEC-030 R-6).
 *
 * @param allowedCallers {@code X-Caller}로 허용할 서비스 이름
 */
@ConfigurationProperties("vlm.media.internal")
public record InternalCallerProperties(List<String> allowedCallers) {

  public InternalCallerProperties {
    allowedCallers = List.copyOf(allowedCallers);
  }
}
