package com.vlm.gateway.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jwt.SignedJWT;
import com.vlm.gateway.support.TestTokens;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class CachingJwkSourceTest {

  private static final String PATH = "/.well-known/jwks.json";

  private WireMockServer server;
  private CachingJwkSource source;

  @BeforeEach
  void setUp() {
    server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    server.start();
    server.stubFor(
        get(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(TestTokens.jwks())));
    source =
        new CachingJwkSource(
            WebClient.create(),
            server.baseUrl() + PATH,
            Duration.ofMinutes(5),
            Duration.ofSeconds(10),
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    server.stop();
  }

  private static SignedJWT parse(String token) throws ParseException {
    return SignedJWT.parse(token);
  }

  @Test
  @DisplayName("[SPEC-001 NFR-5] 모르는 kid가 연달아 와도 재조회 제한 시간 안에는 공개키 목록을 한 번만 다시 받는다")
  void limitsRefetchOnUnknownKeyId() throws ParseException {
    source.select(parse(TestTokens.signedWith(TestTokens.SIGNING_KEY))).collectList().block();

    for (int i = 0; i < 5; i++) {
      assertThat(
              source
                  .select(parse(TestTokens.signedWith(TestTokens.UNKNOWN_KEY)))
                  .collectList()
                  .block())
          .isEmpty();
    }

    // 최초 1회 + 모르는 kid로 인한 재조회 1회
    server.verify(2, getRequestedFor(urlEqualTo(PATH)));
  }
}
