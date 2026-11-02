package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class TokenRejectionTest extends GatewayIntegrationTest {

  private static final String PROTECTED_PATH = "/api/v1/media/videos";

  private WebTestClient.ResponseSpec requestWith(String authorization) {
    return webTestClient
        .get()
        .uri(PROTECTED_PATH)
        .headers(
            h -> {
              if (authorization != null) {
                h.set("Authorization", authorization);
              }
            })
        .exchange();
  }

  private void expectRejected(WebTestClient.ResponseSpec response, String code) {
    response
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.code")
        .isEqualTo(code);
    downstream().verify(0, anyRequestedFor(urlPathMatching("/api/.*")));
  }

  @Test
  @DisplayName("[SPEC-001 R-14] 보호된 경로에 토큰이 없으면 401 AUTH_TOKEN_MISSING을 반환하고 전달하지 않는다")
  void missingToken() {
    expectRejected(requestWith(null), "AUTH_TOKEN_MISSING");
  }

  static Stream<Arguments> invalidTokens() {
    return Stream.of(
        Arguments.of("JWKS에 없는 키로 서명", TestTokens.signedWith(TestTokens.UNKNOWN_KEY)),
        Arguments.of("kid만 같은 다른 키로 서명", TestTokens.signedWith(TestTokens.FORGED_KEY)),
        Arguments.of("발급자 불일치", TestTokens.with(c -> c.issuer("someone-else"))),
        Arguments.of("대상 불일치", TestTokens.with(c -> c.audience("other-api"))),
        Arguments.of("HS256 서명", TestTokens.hmacSigned()),
        Arguments.of("JWT 형식 아님", "not-a-jwt"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidTokens")
  @DisplayName("[SPEC-001 R-15] 서명, 발급자, 대상, 알고리즘 중 하나라도 맞지 않으면 401 AUTH_TOKEN_INVALID")
  void invalidToken(String reason, String token) {
    expectRejected(requestWith("Bearer " + token), "AUTH_TOKEN_INVALID");
  }

  @Test
  @DisplayName("[SPEC-001 R-16] 만료된 토큰이면 401 AUTH_TOKEN_EXPIRED를 반환한다")
  void expiredToken() {
    String token =
        TestTokens.with(
            c ->
                c.issueTime(TestTokens.toDate(Instant.now().minusSeconds(1000)))
                    .expirationTime(TestTokens.toDate(Instant.now().minusSeconds(60))));

    expectRejected(requestWith("Bearer " + token), "AUTH_TOKEN_EXPIRED");
  }

  @Test
  @DisplayName("[SPEC-001 R-16] 만료 후 허용 오차(30초) 안이면 받아들인다")
  void expiredWithinClockSkew() {
    downstream().stubFor(get(urlEqualTo(PROTECTED_PATH)).willReturn(ok()));
    String token =
        TestTokens.with(
            c ->
                c.issueTime(TestTokens.toDate(Instant.now().minusSeconds(910)))
                    .expirationTime(TestTokens.toDate(Instant.now().minusSeconds(10))));

    requestWith("Bearer " + token).expectStatus().isOk();
  }
}
