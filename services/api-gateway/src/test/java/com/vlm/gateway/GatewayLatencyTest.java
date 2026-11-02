package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatewayLatencyTest extends GatewayIntegrationTest {

  private static final String PATH = "/api/v1/media/videos";
  // JIT 컴파일과 연결 풀이 안정될 때까지 버리는 요청 수
  private static final int WARM_UP = 100;
  private static final int SAMPLES = 300;
  // SPEC-001 NFR-1: 게이트웨이가 더하는 지연 p95 50ms 이하
  private static final Duration P95_LIMIT = Duration.ofMillis(50);

  @Test
  @DisplayName("[SPEC-001 NFR-1] 토큰 검증과 전달을 포함한 응답 지연 p95가 50ms 이하다")
  void p95WithinLimit() {
    // 하위 서비스 처리 시간까지 포함해 재므로 NFR-1(하위 처리 시간 제외)보다 엄격한 기준이다.
    downstream().stubFor(get(urlEqualTo(PATH)).willReturn(aResponse().withStatus(200)));
    String token = TestTokens.valid(UUID.randomUUID().toString(), "USER");

    for (int i = 0; i < WARM_UP; i++) {
      call(token);
    }
    List<Long> nanos = new ArrayList<>();
    for (int i = 0; i < SAMPLES; i++) {
      long start = System.nanoTime();
      call(token);
      nanos.add(System.nanoTime() - start);
    }

    Collections.sort(nanos);
    Duration p95 = Duration.ofNanos(nanos.get((int) Math.ceil(SAMPLES * 0.95) - 1));
    assertThat(p95).isLessThanOrEqualTo(P95_LIMIT);
  }

  private void call(String token) {
    webTestClient
        .get()
        .uri(PATH)
        .headers(h -> h.setBearerAuth(token))
        .exchange()
        .expectStatus()
        .isOk();
  }
}
