package com.vlm.gateway.config;

import com.vlm.gateway.error.ProblemAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity
class SecurityConfig {

  @Bean
  SecurityWebFilterChain securityWebFilterChain(
      ServerHttpSecurity http, ProblemAuthenticationEntryPoint entryPoint) {
    return http
        // 세션·폼 로그인을 쓰지 않는 토큰 인증이다. 재발급 쿠키는 SameSite=Strict라 CSRF 토큰 없이 둔다.
        .cors(Customizer.withDefaults())
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .logout(ServerHttpSecurity.LogoutSpec::disable)
        .authorizeExchange(
            exchange ->
                exchange
                    // SPEC-001 R-13: 토큰 없이 전달하는 경로
                    .pathMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/signup",
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh")
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .permitAll())
        .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
        .oauth2ResourceServer(
            oauth2 -> oauth2.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint))
        .build();
  }
}
