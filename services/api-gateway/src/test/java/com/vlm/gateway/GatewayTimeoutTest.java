package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

// 운영 기본값(10초) 대신 짧은 제한 시간으로 빠르게 검증한다.
@TestPropertySource(
    properties = "spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms")
class GatewayTimeoutTest extends GatewayIntegrationTest {

  @Test
  @DisplayName("[SPEC-001 R-19] 하위 서비스가 제한 시간 안에 응답하지 않으면 504 GATEWAY_TIMEOUT을 반환한다")
  void downstreamTimeout() {
    String path = "/api/v1/media/slow";
    downstream()
        .stubFor(
            get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withFixedDelay(2000)));

    GatewayErrorTest.expectProblem(
        webTestClient
            .get()
            .uri(path)
            .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
            .exchange(),
        HttpStatus.GATEWAY_TIMEOUT,
        "GATEWAY_TIMEOUT");
  }

  @Test
  @DisplayName("[SPEC-001 R-19] 오래 열려 있는 알림 경로에는 응답 제한 시간을 적용하지 않는다")
  void notificationRouteHasNoTimeout() {
    String path = "/api/v1/notify/slow";
    downstream()
        .stubFor(
            get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withFixedDelay(1000)));

    webTestClient
        .get()
        .uri(path)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectStatus()
        .isOk();
  }
}
