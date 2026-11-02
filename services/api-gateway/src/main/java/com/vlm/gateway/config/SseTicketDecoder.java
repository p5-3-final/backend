package com.vlm.gateway.config;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;

/** 알림 연결 티켓({@code aud: vlm-sse}) 검증기. 서명·알고리즘·iss·만료 규칙은 접근 토큰과 같다. */
public final class SseTicketDecoder {

  private final ReactiveJwtDecoder delegate;

  SseTicketDecoder(ReactiveJwtDecoder delegate) {
    this.delegate = delegate;
  }

  /** 티켓을 검증한다. 검증에 실패하면 {@code JwtException}으로 끝난다. */
  public Mono<Jwt> decode(String ticket) {
    return delegate.decode(ticket);
  }
}
