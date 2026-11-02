package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.nimbusds.jose.jwk.ECKey;
import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

// 캐시 갱신 주기(운영 5분)를 짧게 줄여 키 제거까지 검증한다. 재조회 제한은 테스트 메서드끼리 상태를 공유하므로 끄고,
// CachingJwkSourceTest에서 따로 검증한다.
@TestPropertySource(
    properties = {"vlm.gateway.jwt.jwks-cache-ttl=1s", "vlm.gateway.jwt.jwks-refetch-cooldown=0s"})
class KeyRotationTest extends GatewayIntegrationTest {

  private static final String PATH = "/api/v1/media/videos";

  @BeforeEach
  void stubDownstream() {
    downstream().stubFor(get(urlEqualTo(PATH)).willReturn(ok()));
  }

  private void publishKeys(ECKey... keys) {
    downstream()
        .stubFor(
            get(urlEqualTo(JWKS_PATH))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(TestTokens.jwks(keys))));
  }

  private WebTestClient.ResponseSpec requestSignedWith(ECKey key) {
    return webTestClient
        .get()
        .uri(PATH)
        .headers(h -> h.setBearerAuth(TestTokens.signedWith(key)))
        .exchange();
  }

  @Test
  @DisplayName("[SPEC-001 NFR-5] 모르는 kid의 토큰이 오면 공개키 목록을 다시 받아 새 키를 받아들인다")
  void refetchesOnUnknownKeyId() {
    requestSignedWith(TestTokens.SIGNING_KEY).expectStatus().isOk();

    publishKeys(TestTokens.SIGNING_KEY, TestTokens.NEXT_KEY);

    requestSignedWith(TestTokens.NEXT_KEY).expectStatus().isOk();
  }

  @Test
  @DisplayName("[SPEC-001 NFR-5] 키 교체 중에는 교체 전 키로 서명한 토큰도 계속 받아들인다")
  void keepsAcceptingPreviousKeyDuringRotation() {
    publishKeys(TestTokens.SIGNING_KEY, TestTokens.NEXT_KEY);

    requestSignedWith(TestTokens.NEXT_KEY).expectStatus().isOk();
    requestSignedWith(TestTokens.SIGNING_KEY).expectStatus().isOk();
  }

  @Test
  @DisplayName("[SPEC-001 NFR-5] 캐시 갱신 주기가 지나면 목록에서 빠진 이전 키의 토큰을 거부한다")
  void dropsRemovedKeyAfterCacheTtl() throws InterruptedException {
    requestSignedWith(TestTokens.SIGNING_KEY).expectStatus().isOk();

    publishKeys(TestTokens.NEXT_KEY);
    Thread.sleep(1500);

    requestSignedWith(TestTokens.SIGNING_KEY).expectStatus().isUnauthorized();
  }
}
