package com.vlm.gateway.error;

import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * {@code RequestRateLimiter}가 거절할 때 보내는 빈 429 응답을 계약의 ProblemDetail과 {@code Retry-After}로
 * 바꾼다(SPEC-001 R-30, 계약 {@code _common.yaml} RateLimited).
 */
@Component
@RequiredArgsConstructor
public class RateLimitedResponseFilter implements GlobalFilter, Ordered {

  // RedisRateLimiter가 응답에 남기는 헤더
  private static final String REMAINING = "X-RateLimit-Remaining";
  private static final String REPLENISH_RATE = "X-RateLimit-Replenish-Rate";
  private static final String REQUESTED_TOKENS = "X-RateLimit-Requested-Tokens";

  private final ProblemResponseWriter writer;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpResponse response = exchange.getResponse();
    ServerHttpResponseDecorator decorated =
        new ServerHttpResponseDecorator(response) {
          // 거절할 때 RequestRateLimiter는 본문 없이 setComplete()로 끝낸다. 하위 서비스가 준 429는 writeWith로 오므로 건드리지
          // 않는다.
          @Override
          public Mono<Void> setComplete() {
            if (HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode())) {
              getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds(this)));
              return writer.write(exchange, GatewayErrorCode.RATE_LIMITED);
            }
            return super.setComplete();
          }
        };
    return chain.filter(exchange.mutate().response(decorated).build());
  }

  // 다음 요청에 필요한 토큰이 다시 찰 때까지의 초. 헤더가 없으면 1초 뒤로 안내한다.
  private static long retryAfterSeconds(ServerHttpResponse response) {
    HttpHeaders headers = response.getHeaders();
    long remaining = longHeader(headers, REMAINING, 0);
    long requested = longHeader(headers, REQUESTED_TOKENS, 1);
    long rate = Math.max(1, longHeader(headers, REPLENISH_RATE, 1));
    long missing = Math.max(1, requested - remaining);
    return (missing + rate - 1) / rate;
  }

  private static long longHeader(HttpHeaders headers, String name, long fallback) {
    String value = headers.getFirst(name);
    if (value == null) {
      return fallback;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  @Override
  public int getOrder() {
    // 라우트 필터(RequestRateLimiter)보다 먼저 응답을 감싸야 한다.
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
