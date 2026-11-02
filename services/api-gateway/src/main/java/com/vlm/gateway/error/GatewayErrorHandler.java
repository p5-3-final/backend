package com.vlm.gateway.error;

import java.net.ConnectException;
import java.net.UnknownHostException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.cloud.gateway.support.TimeoutException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 라우팅 중 생긴 오류를 계약의 게이트웨이 오류 코드로 바꾼다(SPEC-001 R-18~R-20).
 *
 * <p>Spring Boot 기본 오류 핸들러(order -1)보다 먼저 실행되도록 -2로 둔다.
 */
@Component
@Order(-2)
@RequiredArgsConstructor
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

  private final ProblemResponseWriter writer;

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(exception);
    }
    return writer.write(exchange, resolve(exception));
  }

  private static GatewayErrorCode resolve(Throwable exception) {
    if (exception instanceof ResponseStatusException status
        && status.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
      return GatewayErrorCode.ROUTE_NOT_FOUND;
    }
    // 라우트 응답 제한 시간(httpclient.response-timeout)을 넘기면 Spring Cloud Gateway가 504로 알린다.
    if (exception instanceof TimeoutException
        || (exception instanceof ResponseStatusException status
            && status.getStatusCode().isSameCodeAs(HttpStatus.GATEWAY_TIMEOUT))) {
      return GatewayErrorCode.TIMEOUT;
    }
    if (isConnectionFailure(exception)) {
      return GatewayErrorCode.UNAVAILABLE;
    }
    log.error("Unhandled gateway exception", exception);
    return GatewayErrorCode.INTERNAL_ERROR;
  }

  // Netty는 연결 거부·주소 해석 실패를 여러 겹으로 감싸서 던지므로 원인 사슬을 따라가며 찾는다.
  private static boolean isConnectionFailure(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException || cause instanceof UnknownHostException) {
        return true;
      }
    }
    return false;
  }
}
