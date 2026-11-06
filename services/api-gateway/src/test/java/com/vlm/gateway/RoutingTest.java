package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoutingTest extends GatewayIntegrationTest {

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "/api/v1/auth/ping",
        "/api/v1/media/ping",
        "/api/v1/jobs/ping",
        "/api/v1/analysis/ping",
        "/api/v1/search/ping",
        "/api/v1/notify/ping",
        "/api/v1/quality/ping"
      })
  @DisplayName("서비스 경로로 들어온 요청을 경로를 유지한 채 해당 서비스로 전달한다")
  void forwardsToService(String path) {
    downstream()
        .stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody("ok")));

    webTestClient
        .get()
        .uri(path)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
  }
}
