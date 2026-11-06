package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QualityRoutingTest extends GatewayIntegrationTest {

  private static final String INTERNAL = "/internal/v1/quality/evaluation-runs";

  @Test
  @DisplayName("[SPEC-029 R-26] 품질 서비스 내부 경로는 전달하지 않고 404를 반환한다")
  void doesNotForwardInternalPath() {
    downstream()
        .stubFor(get(urlEqualTo(INTERNAL)).willReturn(aResponse().withStatus(200).withBody("ok")));

    webTestClient
        .get()
        .uri(INTERNAL)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "OPERATOR")))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("GATEWAY_ROUTE_NOT_FOUND");

    downstream().verify(0, anyRequestedFor(urlPathMatching("/internal/.*")));
  }
}
