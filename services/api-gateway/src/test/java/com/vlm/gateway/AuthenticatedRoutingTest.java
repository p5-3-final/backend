package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthenticatedRoutingTest extends GatewayIntegrationTest {

  private static final String PATH = "/api/v1/media/videos";

  @Test
  @DisplayName("[SPEC-001 R-12] 유효한 토큰이면 해당 서비스로 전달하고 검증된 사용자 ID와 역할을 헤더로 붙인다")
  void forwardsWithUserHeaders() {
    String userId = UUID.randomUUID().toString();
    downstream().stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)));

    webTestClient
        .get()
        .uri(PATH)
        .headers(h -> h.setBearerAuth(TestTokens.valid(userId, "USER", "OPERATOR")))
        .exchange()
        .expectStatus()
        .isOk();

    downstream()
        .verify(
            getRequestedFor(urlEqualTo(PATH))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Roles", equalTo("USER,OPERATOR")));
  }
}
