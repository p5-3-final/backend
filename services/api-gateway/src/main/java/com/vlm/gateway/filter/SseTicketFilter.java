package com.vlm.gateway.filter;

import com.vlm.gateway.config.SseTicketDecoder;
import com.vlm.gateway.error.GatewayErrorCode;
import com.vlm.gateway.error.ProblemResponseWriter;
import java.net.URI;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * 알림 연결 경로에서 {@code ?ticket}으로 받은 티켓을 검증하고 한 번만 쓰게 한다(SPEC-001 R-27, R-28).
 *
 * <p>형식과 1회용 규칙은 계약 {@code access-token.md}의 "알림 연결 티켓"을 따른다.
 */
@Component
@RequiredArgsConstructor
public class SseTicketFilter implements GlobalFilter, Ordered {

  public static final String PATH = "/api/v1/notify/stream";

  private static final String TICKET_PARAM = "ticket";
  private static final String USED_KEY_PREFIX = "sse:jti:";
  // 티켓 유효기간 30초 + exp 허용 오차 30초. 그 뒤에는 만료 검증에서 거절되므로 기록을 지워도 된다.
  private static final Duration USED_KEY_TTL = Duration.ofSeconds(60);

  private final SseTicketDecoder decoder;
  private final ReactiveStringRedisTemplate redis;
  private final ProblemResponseWriter writer;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!PATH.equals(exchange.getRequest().getPath().value())) {
      return chain.filter(exchange);
    }
    String ticket = exchange.getRequest().getQueryParams().getFirst(TICKET_PARAM);
    if (ticket == null || ticket.isBlank()) {
      return reject(exchange);
    }
    return decoder
        .decode(ticket)
        .onErrorResume(JwtException.class, e -> Mono.empty())
        .filterWhen(this::markUsed)
        .flatMap(jwt -> chain.filter(forward(exchange, jwt)).thenReturn(true))
        .switchIfEmpty(Mono.defer(() -> reject(exchange).thenReturn(false)))
        .then();
  }

  // SET NX가 실패하면 이미 쓴 티켓이다.
  private Mono<Boolean> markUsed(Jwt jwt) {
    String jti = jwt.getId();
    if (jti == null) {
      return Mono.just(false);
    }
    return redis.opsForValue().setIfAbsent(USED_KEY_PREFIX + jti, "1", USED_KEY_TTL);
  }

  // 티켓이 하위 서비스 접근 로그에 남지 않도록 쿼리에서 지우고 전달한다(NFR-3).
  private static ServerWebExchange forward(ServerWebExchange exchange, Jwt jwt) {
    URI uri =
        UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
            .replaceQueryParam(TICKET_PARAM)
            .build(true)
            .toUri();
    ServerWebExchange withoutTicket =
        exchange.mutate().request(exchange.getRequest().mutate().uri(uri).build()).build();
    return UserHeaderFilter.withUser(withoutTicket, jwt);
  }

  private Mono<Void> reject(ServerWebExchange exchange) {
    return writer.write(exchange, GatewayErrorCode.TICKET_INVALID);
  }

  @Override
  public int getOrder() {
    // 클라이언트가 보낸 사용자 헤더를 지우는 UserHeaderFilter 다음에 티켓 주인으로 다시 채운다.
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
