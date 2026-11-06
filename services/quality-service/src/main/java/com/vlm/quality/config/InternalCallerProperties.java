package com.vlm.quality.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 내부 경로({@code /internal/v1/quality/**}) 호출자 허용 목록(R-17).
 *
 * @param allowedCallers {@code X-Caller}로 허용할 잡 이름
 */
@ConfigurationProperties("vlm.quality.internal")
public record InternalCallerProperties(List<String> allowedCallers) {

  public InternalCallerProperties {
    allowedCallers = List.copyOf(allowedCallers);
  }
}
