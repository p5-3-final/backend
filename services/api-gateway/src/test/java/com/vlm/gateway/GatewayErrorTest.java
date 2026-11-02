package com.vlm.gateway;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class GatewayErrorTest extends GatewayIntegrationTest {

  private WebTestClient.ResponseSpec getWithToken(String path) {
    return webTestClient
        .get()
        .uri(path)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange();
  }

  static void expectProblem(WebTestClient.ResponseSpec response, HttpStatus status, String code) {
    response
        .expectStatus()
        .isEqualTo(status)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(status.value())
        .jsonPath("$.code")
        .isEqualTo(code);
  }

  @Test
  @DisplayName("[SPEC-001 R-18] 어느 서비스에도 해당하지 않는 경로면 404 GATEWAY_ROUTE_NOT_FOUND를 반환한다")
  void routeNotFound() {
    expectProblem(
        getWithToken("/api/v1/unknown/resource"), HttpStatus.NOT_FOUND, "GATEWAY_ROUTE_NOT_FOUND");
  }
}
