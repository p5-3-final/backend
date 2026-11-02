package com.vlm.gateway.config;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * auth-service 공개키 목록 캐시. 규칙은 docs 레포 {@code access-token.md}의 "키 교체 규칙"이다.
 *
 * <ul>
 *   <li>캐시는 {@code ttl}이 지나면 다시 받는다. 목록에서 빠진 키를 계속 믿지 않기 위해서다.
 *   <li>모르는 {@code kid}가 오면 즉시 다시 받아 새 키를 확인한다. 다만 임의의 kid로 auth-service에 요청을 몰지 못하도록 {@code
 *       refetchCooldown}에 한 번만 한다.
 * </ul>
 */
class CachingJwkSource {

  private record Snapshot(JWKSet keys, Instant fetchedAt) {}

  private final WebClient webClient;
  private final String jwksUri;
  private final Duration ttl;
  private final Duration refetchCooldown;
  private final Clock clock;
  private final AtomicReference<@Nullable Snapshot> snapshot = new AtomicReference<>();
  private final AtomicLong lastMissRefetchMillis = new AtomicLong(0);

  CachingJwkSource(
      WebClient webClient, String jwksUri, Duration ttl, Duration refetchCooldown, Clock clock) {
    this.webClient = webClient;
    this.jwksUri = jwksUri;
    this.ttl = ttl;
    this.refetchCooldown = refetchCooldown;
    this.clock = clock;
  }

  Flux<JWK> select(SignedJWT jwt) {
    JWKSelector selector = new JWKSelector(JWKMatcher.forJWSHeader(jwt.getHeader()));
    return current()
        .flatMapMany(
            keys -> {
              List<JWK> matched = selector.select(keys);
              if (!matched.isEmpty() || !claimMissRefetch()) {
                return Flux.fromIterable(matched);
              }
              return fetch().flatMapIterable(selector::select);
            });
  }

  private Mono<JWKSet> current() {
    Snapshot cached = snapshot.get();
    if (cached != null && cached.fetchedAt().plus(ttl).isAfter(clock.instant())) {
      return Mono.just(cached.keys());
    }
    return fetch();
  }

  private boolean claimMissRefetch() {
    long now = clock.millis();
    long last = lastMissRefetchMillis.get();
    return now - last > refetchCooldown.toMillis()
        && lastMissRefetchMillis.compareAndSet(last, now);
  }

  private Mono<JWKSet> fetch() {
    return webClient
        .get()
        .uri(jwksUri)
        .retrieve()
        .bodyToMono(String.class)
        .map(CachingJwkSource::parse)
        .doOnNext(keys -> snapshot.set(new Snapshot(keys, clock.instant())));
  }

  private static JWKSet parse(String body) {
    try {
      return JWKSet.parse(body);
    } catch (ParseException e) {
      throw new IllegalStateException("auth-service 공개키 목록을 읽을 수 없습니다.", e);
    }
  }
}
