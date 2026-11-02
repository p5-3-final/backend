package com.vlm.gateway.error;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.common.error.ErrorCode;
import io.micrometer.tracing.handler.TracingObservationHandler;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 게이트웨이 자체 오류를 RFC 9457 ProblemDetail({@code application/problem+json})로 쓴다. 추적 중이면 {@code
 * traceId}를 넣는다.
 */
@Component
@RequiredArgsConstructor
public class ProblemResponseWriter {

  private final ObjectMapper objectMapper;

  public Mono<Void> write(ServerWebExchange exchange, ErrorCode errorCode) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(errorCode.status()), errorCode.defaultMessage());
    problem.setProperty("code", errorCode.code());
    traceId(exchange).ifPresent(traceId -> problem.setProperty("traceId", traceId));
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatusCode.valueOf(errorCode.status()));
    response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return response.writeWith(Mono.fromCallable(() -> toBuffer(response, problem)));
  }

  // WebFlux는 요청 처리 스레드가 바뀌어 MDC를 믿을 수 없으므로, 요청에 붙은 관측 컨텍스트에서 현재 span을 찾는다(NFR-4).
  private static Optional<String> traceId(ServerWebExchange exchange) {
    return ServerRequestObservationContext.findCurrent(exchange.getAttributes())
        .map(
            context ->
                context.<TracingObservationHandler.TracingContext>get(
                    TracingObservationHandler.TracingContext.class))
        .map(TracingObservationHandler.TracingContext::getSpan)
        .map(span -> span.context().traceId());
  }

  private DataBuffer toBuffer(ServerHttpResponse response, ProblemDetail problem)
      throws JsonProcessingException {
    return response.bufferFactory().wrap(objectMapper.writeValueAsBytes(problem));
  }
}
