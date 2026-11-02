package com.vlm.gateway.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/** 게이트웨이 통합 테스트 공통 설정. 하위 서비스와 auth-service JWKS를 WireMock 하나로 대신한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class GatewayIntegrationTest {

  protected static final String JWKS_PATH = "/.well-known/jwks.json";

  @Autowired protected WebTestClient webTestClient;

  @DynamicPropertySource
  static void gatewayProperties(DynamicPropertyRegistry registry) {
    registerProperties(registry);
  }

  public static void registerProperties(DynamicPropertyRegistry registry) {
    DownstreamServices.register(registry);
    registry.add("GATEWAY_JWKS_URI", () -> DownstreamServices.server().baseUrl() + JWKS_PATH);
    registry.add("GATEWAY_JWT_ISSUER", () -> TestTokens.ISSUER);
  }

  @BeforeEach
  void resetDownstream() {
    stubJwks();
  }

  /** WireMock을 초기화하고 auth-service JWKS 응답을 다시 등록한다. */
  public static void stubJwks() {
    downstream().resetAll();
    downstream()
        .stubFor(
            get(urlEqualTo(JWKS_PATH))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(TestTokens.jwks())));
  }

  protected static WireMockServer downstream() {
    return DownstreamServices.server();
  }
}
