package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = "GATEWAY_CORS_ALLOWED_ORIGINS=https://app.example.com")
class CorsTest extends GatewayIntegrationTest {

  private static final String PATH = "/api/v1/media/videos";
  private static final String ALLOWED = "https://app.example.com";
  private static final String DENIED = "https://evil.example.com";

  @Test
  @DisplayName("[SPEC-001 R-21] 허용된 출처의 사전 요청에는 출처와 자격 증명을 허용하는 헤더로 응답한다")
  void preflightFromAllowedOrigin() {
    webTestClient
        .options()
        .uri(PATH)
        .header(HttpHeaders.ORIGIN, ALLOWED)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, HttpHeaders.AUTHORIZATION)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED)
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
  }

  @Test
  @DisplayName("[SPEC-001 R-21] 허용 목록 밖 출처의 사전 요청에는 교차 출처 허용 헤더를 주지 않는다")
  void preflightFromDeniedOrigin() {
    webTestClient
        .options()
        .uri(PATH)
        .header(HttpHeaders.ORIGIN, DENIED)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name())
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Test
  @DisplayName("[SPEC-001 R-21] 허용 목록 밖 출처의 실제 요청에도 교차 출처 허용 헤더를 주지 않는다")
  void actualRequestFromDeniedOrigin() {
    downstream().stubFor(get(urlEqualTo(PATH)).willReturn(ok()));

    webTestClient
        .get()
        .uri(PATH)
        .header(HttpHeaders.ORIGIN, DENIED)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectHeader()
        .doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
  }

  @Test
  @DisplayName("[SPEC-001 R-21] 허용된 출처의 실제 요청에는 교차 출처 허용 헤더를 준다")
  void actualRequestFromAllowedOrigin() {
    downstream().stubFor(get(urlEqualTo(PATH)).willReturn(ok()));

    webTestClient
        .get()
        .uri(PATH)
        .header(HttpHeaders.ORIGIN, ALLOWED)
        .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED);
  }
}
