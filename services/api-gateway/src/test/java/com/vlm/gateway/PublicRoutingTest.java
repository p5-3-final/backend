package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PublicRoutingTest extends GatewayIntegrationTest {

  @ParameterizedTest(name = "POST {0}")
  @ValueSource(strings = {"/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh"})
  @DisplayName("[SPEC-001 R-13] 가입·로그인·토큰 재발급은 토큰 없이 auth-service로 전달한다")
  void publicAuthPaths(String path) {
    downstream().stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(200)));

    webTestClient.post().uri(path).exchange().expectStatus().isOk();
  }

  @Test
  @DisplayName("[SPEC-001 R-13] health는 토큰 없이 응답한다")
  void health() {
    webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }
}
