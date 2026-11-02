package com.vlm.gateway.filter;

import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 하위 서비스에 검증된 사용자 정보를 헤더로 전달한다(계약 {@code _common.yaml}의 XUserId, XUserRoles).
 *
 * <p>하위 서비스는 이 헤더만 믿고 인가하므로, 클라이언트가 보낸 같은 이름의 헤더는 항상 먼저 지운다.
 */
@Component
public class UserHeaderFilter implements GlobalFilter, Ordered {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerWebExchange stripped = withoutUserHeaders(exchange);
    return stripped
        .getPrincipal()
        .filter(JwtAuthenticationToken.class::isInstance)
        .map(principal -> withUser(stripped, ((JwtAuthenticationToken) principal).getToken()))
        .defaultIfEmpty(stripped)
        .flatMap(chain::filter);
  }

  // SPEC-001 R-17: 공개 경로를 포함한 모든 요청에서 클라이언트가 보낸 사용자 헤더를 지운다.
  private static ServerWebExchange withoutUserHeaders(ServerWebExchange exchange) {
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.remove(USER_ID);
                  headers.remove(USER_ROLES);
                })
            .build();
    return exchange.mutate().request(request).build();
  }

  private static ServerWebExchange withUser(ServerWebExchange exchange, Jwt jwt) {
    List<String> roles = jwt.getClaimAsStringList("roles");
    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.set(USER_ID, jwt.getSubject());
                  headers.set(USER_ROLES, roles == null ? "" : String.join(",", roles));
                })
            .build();
    return exchange.mutate().request(request).build();
  }

  @Override
  public int getOrder() {
    // 라우팅 필터보다 먼저 실행되어야 하위 서비스로 가는 요청에 헤더가 실린다.
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
