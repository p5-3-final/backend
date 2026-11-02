package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HeaderSpoofingTest extends GatewayIntegrationTest {

  private static final String SPOOFED_ID = "00000000-0000-0000-0000-000000000000";

  @Test
  @DisplayName("[SPEC-001 R-17] 공개 경로에서 클라이언트가 보낸 사용자 헤더는 전달하지 않는다")
  void stripsOnPublicPath() {
    String path = "/api/v1/auth/login";
    downstream().stubFor(post(urlEqualTo(path)).willReturn(ok()));

    webTestClient
        .post()
        .uri(path)
        .header("X-User-Id", SPOOFED_ID)
        .header("X-User-Roles", "ADMIN")
        .exchange()
        .expectStatus()
        .isOk();

    downstream()
        .verify(
            postRequestedFor(urlEqualTo(path))
                .withHeader("X-User-Id", absent())
                .withHeader("X-User-Roles", absent()));
  }

  @Test
  @DisplayName("[SPEC-001 R-17] 보호된 경로에서는 클라이언트가 보낸 값 대신 토큰의 값만 전달한다")
  void replacesOnProtectedPath() {
    String path = "/api/v1/media/videos";
    String userId = UUID.randomUUID().toString();
    downstream().stubFor(get(urlEqualTo(path)).willReturn(ok()));

    webTestClient
        .get()
        .uri(path)
        .headers(h -> h.setBearerAuth(TestTokens.valid(userId, "USER")))
        .header("X-User-Id", SPOOFED_ID)
        .header("X-User-Roles", "ADMIN")
        .exchange()
        .expectStatus()
        .isOk();

    downstream()
        .verify(
            getRequestedFor(urlEqualTo(path))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Roles", equalTo("USER")));
  }
}
