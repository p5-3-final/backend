package com.vlm.gateway.error;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 인증 실패를 계약의 401 오류 코드로 바꾼다(SPEC-001 R-14~R-16). */
@Component
@RequiredArgsConstructor
public class ProblemAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

  private static final String EXPIRED_DESCRIPTION_PREFIX = "Jwt expired";

  private final ProblemResponseWriter writer;

  @Override
  public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException exception) {
    return writer.write(exchange, resolve(exception));
  }

  private static GatewayErrorCode resolve(AuthenticationException exception) {
    // Bearer 토큰 없이 보호된 경로에 오면 인가 단계에서 이 예외로 넘어온다. 토큰이 있었다면 검증 실패다.
    if (exception instanceof AuthenticationCredentialsNotFoundException) {
      return GatewayErrorCode.TOKEN_MISSING;
    }
    if (isExpired(exception)) {
      return GatewayErrorCode.TOKEN_EXPIRED;
    }
    return GatewayErrorCode.TOKEN_INVALID;
  }

  private static boolean isExpired(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof JwtValidationException validation) {
        // JwtTimestampValidator가 exp 위반에 남기는 설명이다. 다른 검증 실패와 섞여 있어도 만료를 우선한다.
        return validation.getErrors().stream()
            .anyMatch(
                error ->
                    error.getDescription() != null
                        && error.getDescription().startsWith(EXPIRED_DESCRIPTION_PREFIX));
      }
    }
    return false;
  }
}
