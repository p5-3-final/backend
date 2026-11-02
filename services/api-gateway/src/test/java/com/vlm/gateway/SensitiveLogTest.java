package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.vlm=DEBUG")
class SensitiveLogTest extends GatewayIntegrationTest {

  @Test
  @DisplayName("[SPEC-001 NFR-3] 접근 토큰과 알림 티켓 원문을 로그에 남기지 않는다")
  void doesNotLogSecrets(CapturedOutput output) {
    String userId = UUID.randomUUID().toString();
    String accessToken = TestTokens.valid(userId, "USER");
    String forgedToken = TestTokens.signedWith(TestTokens.UNKNOWN_KEY);
    String ticket = TestTokens.ticket(userId, "USER");
    downstream().stubFor(get(urlPathEqualTo("/api/v1/media/videos")).willReturn(aResponse()));
    downstream().stubFor(get(urlPathEqualTo("/api/v1/notify/stream")).willReturn(aResponse()));

    webTestClient
        .get()
        .uri("/api/v1/media/videos")
        .headers(h -> h.setBearerAuth(accessToken))
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .get()
        .uri("/api/v1/media/videos")
        .headers(h -> h.setBearerAuth(forgedToken))
        .exchange()
        .expectStatus()
        .isUnauthorized();
    webTestClient
        .get()
        .uri("/api/v1/notify/stream?ticket={t}", ticket)
        .exchange()
        .expectStatus()
        .isOk();
    webTestClient
        .get()
        .uri("/api/v1/notify/stream?ticket={t}", ticket)
        .exchange()
        .expectStatus()
        .isUnauthorized();

    for (String secret : List.of(accessToken, forgedToken, ticket)) {
      assertThat(output.getAll()).doesNotContain(secret);
    }
  }
}
