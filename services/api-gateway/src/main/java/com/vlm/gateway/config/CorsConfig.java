package com.vlm.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Spring Security의 CORS 필터가 쓰는 설정. 인증보다 먼저 처리되어야 토큰 없는 사전 요청(OPTIONS)이 401로 막히지 않는다.
 *
 * <p>Spring Cloud Gateway의 globalcors는 쓰지 않는다. 두 곳에서 설정하면 허용 헤더가 중복된다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayCorsProperties.class)
class CorsConfig {

  @Bean
  CorsConfigurationSource corsConfigurationSource(GatewayCorsProperties properties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(properties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "traceparent"));
    // 재발급 토큰 쿠키를 보내야 하므로 자격 증명을 허용한다. 그래서 출처는 와일드카드 없이 목록으로만 둔다.
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
