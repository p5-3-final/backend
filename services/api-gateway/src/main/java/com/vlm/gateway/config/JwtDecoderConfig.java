package com.vlm.gateway.config;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayJwtProperties.class)
class JwtDecoderConfig {

  // access-token.md: exp 허용 오차 30초
  private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

  // 접근 토큰과 알림 티켓은 같은 키로 서명하므로 공개키 캐시를 하나만 둔다.
  @Bean
  CachingJwkSource jwkSource(GatewayJwtProperties properties, WebClient.Builder webClients) {
    return new CachingJwkSource(
        webClients.build(),
        properties.jwksUri(),
        properties.jwksCacheTtl(),
        properties.jwksRefetchCooldown(),
        Clock.systemUTC());
  }

  @Bean
  ReactiveJwtDecoder jwtDecoder(GatewayJwtProperties properties, CachingJwkSource jwkSource) {
    return decoder(jwkSource, properties.issuer(), properties.audience());
  }

  // ReactiveJwtDecoder 타입으로 하나 더 등록하면 리소스 서버 자동 구성이 어느 것을 쓸지 정하지 못하므로 감싸서 등록한다.
  @Bean
  SseTicketDecoder sseTicketDecoder(GatewayJwtProperties properties, CachingJwkSource jwkSource) {
    return new SseTicketDecoder(
        decoder(jwkSource, properties.issuer(), properties.ticketAudience()));
  }

  private static ReactiveJwtDecoder decoder(
      CachingJwkSource jwkSource, String issuer, String audience) {
    // 계약상 ES256만 허용한다. 다른 알고리즘이나 none으로 서명한 토큰은 검증 단계에서 거부된다.
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withJwkSource(jwkSource::select)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .build();
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            new JwtTimestampValidator(CLOCK_SKEW),
            new JwtIssuerValidator(issuer),
            new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(audience))));
    return decoder;
  }
}
