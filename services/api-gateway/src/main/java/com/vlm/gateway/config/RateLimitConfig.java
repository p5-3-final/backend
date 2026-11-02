package com.vlm.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration(proxyBeanMethods = false)
class RateLimitConfig {

  // 운영에서는 ALB가 X-Forwarded-For 끝에 실제 접속 IP를 붙이므로 마지막 값 하나만 믿는다.
  // 클라이언트가 앞쪽에 넣은 값으로는 다른 IP인 척할 수 없다. 헤더가 없으면 접속 주소를 쓴다.
  private static final int TRUSTED_PROXY_COUNT = 1;

  @Bean
  KeyResolver clientIpKeyResolver() {
    XForwardedRemoteAddressResolver resolver =
        XForwardedRemoteAddressResolver.maxTrustedIndex(TRUSTED_PROXY_COUNT);
    return exchange -> Mono.just(resolver.resolve(exchange).getAddress().getHostAddress());
  }
}
