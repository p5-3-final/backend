package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TraceIdTest extends GatewayIntegrationTest {

  // W3C trace context 예시 값(trace-id 32자 hex)
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
  private static final String TRACEPARENT = "00-" + TRACE_ID + "-00f067aa0ba902b7-01";

  @Test
  @DisplayName("[SPEC-001 NFR-4] 인증 오류 응답에 추적 ID를 넣는다")
  void authenticationErrorHasTraceId() {
    webTestClient
        .get()
        .uri("/api/v1/media/videos")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.traceId")
        .value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{32}"));
  }

  @Test
  @DisplayName("[SPEC-001 NFR-4] 라우팅 오류 응답에 클라이언트가 보낸 추적 ID를 넣는다")
  void routingErrorHasIncomingTraceId() {
    webTestClient
        .get()
        .uri("/api/v1/unknown")
        .header("traceparent", TRACEPARENT)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.traceId")
        .isEqualTo(TRACE_ID);
  }

  @Test
  @DisplayName("[SPEC-001 NFR-4] 하위 서비스로 같은 추적 ID의 traceparent를 전달한다")
  void propagatesTraceparent() {
    String path = "/api/v1/media/videos";
    downstream().stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));

    webTestClient
        .get()
        .uri(path)
        .header("traceparent", TRACEPARENT)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectStatus()
        .isOk();

    downstream()
        .verify(
            getRequestedFor(urlEqualTo(path))
                .withHeader("traceparent", matching("00-" + TRACE_ID + "-[0-9a-f]{16}-0[01]")));
  }
}
