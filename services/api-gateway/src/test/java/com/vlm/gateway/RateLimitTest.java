package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.gateway.support.GatewayIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

class RateLimitTest extends GatewayIntegrationTest {

  private static final String LOGIN = "/api/v1/auth/login";
  private static final String SIGNUP = "/api/v1/auth/signup";
  private static final int LIMIT_PER_MINUTE = 10;

  // Redis 카운터가 다른 테스트와 섞이지 않도록 테스트마다 다른 IP를 쓴다.
  private final String clientIp = randomIp();

  @BeforeEach
  void stubAuthService() {
    downstream()
        .stubFor(post(urlPathMatching("/api/v1/auth/.*")).willReturn(aResponse().withStatus(200)));
  }

  @Test
  @DisplayName(
      "[SPEC-001 R-30] 같은 IP의 로그인이 1분에 10회를 넘으면 429 GATEWAY_RATE_LIMITED와 Retry-After를 반환한다")
  void limitsLogin() {
    for (int i = 0; i < LIMIT_PER_MINUTE; i++) {
      request(LOGIN, clientIp).expectStatus().isOk();
    }

    ResponseSpec limited = request(LOGIN, clientIp);

    GatewayErrorTest.expectProblem(limited, HttpStatus.TOO_MANY_REQUESTS, "GATEWAY_RATE_LIMITED");
    limited
        .expectHeader()
        .value("Retry-After", value -> assertThat(Integer.parseInt(value)).isPositive());
  }

  @Test
  @DisplayName("[SPEC-001 R-30] 로그인과 가입 요청을 합쳐서 센다")
  void countsLoginAndSignupTogether() {
    for (int i = 0; i < LIMIT_PER_MINUTE / 2; i++) {
      request(LOGIN, clientIp).expectStatus().isOk();
      request(SIGNUP, clientIp).expectStatus().isOk();
    }

    request(SIGNUP, clientIp).expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @DisplayName("[SPEC-001 R-30] 다른 IP의 요청과 로그인·가입이 아닌 경로는 제한하지 않는다")
  void doesNotLimitOthers() {
    for (int i = 0; i <= LIMIT_PER_MINUTE; i++) {
      request(LOGIN, clientIp);
    }

    request(LOGIN, randomIp()).expectStatus().isOk();
    request("/api/v1/auth/refresh", clientIp).expectStatus().isOk();
  }

  private ResponseSpec request(String path, String ip) {
    return webTestClient.post().uri(path).header("X-Forwarded-For", ip).exchange();
  }

  private static String randomIp() {
    long bits = UUID.randomUUID().getMostSignificantBits();
    return "10.%d.%d.%d".formatted((bits >>> 16) & 0xff, (bits >>> 8) & 0xff, bits & 0xff);
  }
}
