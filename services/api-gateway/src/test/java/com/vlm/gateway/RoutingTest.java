package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.vlm.gateway.support.DownstreamServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoutingTest {

  @Autowired private WebTestClient webTestClient;

  @DynamicPropertySource
  static void downstream(DynamicPropertyRegistry registry) {
    DownstreamServices.register(registry);
  }

  @BeforeEach
  void resetDownstream() {
    DownstreamServices.server().resetAll();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "/api/v1/auth/ping",
        "/api/v1/media/ping",
        "/api/v1/jobs/ping",
        "/api/v1/analysis/ping",
        "/api/v1/search/ping",
        "/api/v1/notify/ping"
      })
  @DisplayName("서비스 경로로 들어온 요청을 경로를 유지한 채 해당 서비스로 전달한다")
  void forwardsToService(String path) {
    DownstreamServices.server()
        .stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(200).withBody("ok")));

    webTestClient
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .isEqualTo("ok");
  }
}
