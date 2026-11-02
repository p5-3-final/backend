package com.vlm.gateway;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

// 상위 클래스의 주소 등록을 덮어쓸 수 없어서 GatewayIntegrationTest를 상속하지 않고 같은 설정을 직접 한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// 테스트에서는 Spring Boot가 추적을 꺼 두므로, 운영처럼 traceparent를 이어 받도록 켠다.
@AutoConfigureObservability(metrics = false)
class GatewayUnavailableTest {

  @Autowired private WebTestClient webTestClient;

  @DynamicPropertySource
  static void gatewayProperties(DynamicPropertyRegistry registry) {
    GatewayIntegrationTest.registerProperties(registry);
    // media-service만 아무도 듣지 않는 포트로 보낸다.
    registry.add("MEDIA_SERVICE_URI", () -> "http://127.0.0.1:1");
  }

  @BeforeEach
  void stubJwks() {
    GatewayIntegrationTest.stubJwks();
  }

  @Test
  @DisplayName("[SPEC-001 R-20] 전달할 서비스에 연결할 수 없으면 503 GATEWAY_UNAVAILABLE을 반환한다")
  void downstreamUnavailable() {
    GatewayErrorTest.expectProblem(
        webTestClient
            .get()
            .uri("/api/v1/media/videos")
            .headers(h -> h.setBearerAuth(TestTokens.valid(UUID.randomUUID().toString(), "USER")))
            .exchange(),
        HttpStatus.SERVICE_UNAVAILABLE,
        "GATEWAY_UNAVAILABLE");
  }
}
