package com.vlm.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.vlm.gateway.support.GatewayIntegrationTest;
import com.vlm.gateway.support.TestTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec;

class SseTicketTest extends GatewayIntegrationTest {

  private static final String PATH = "/api/v1/notify/stream";

  private final String userId = UUID.randomUUID().toString();

  @BeforeEach
  void stubStream() {
    downstream()
        .stubFor(
            get(urlPathEqualTo(PATH))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("data: connected\n\n")));
  }

  @Test
  @DisplayName("[SPEC-001 R-27] 유효한 티켓이면 티켓 주인의 사용자 ID와 역할을 헤더로 붙여 notification-service로 전달한다")
  void forwardsWithTicketOwner() {
    webTestClient
        .get()
        .uri(PATH + "?ticket={ticket}", TestTokens.ticket(userId, "USER", "OPERATOR"))
        .header("X-User-Id", "spoofed")
        .exchange()
        .expectStatus()
        .isOk();

    downstream()
        .verify(
            getRequestedFor(urlPathEqualTo(PATH))
                .withHeader("X-User-Id", equalTo(userId))
                .withHeader("X-User-Roles", equalTo("USER,OPERATOR"))
                // 티켓이 하위 서비스 로그에 남지 않도록 전달하지 않는다(NFR-3).
                .withQueryParam("ticket", absent()));
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 티켓이 없으면 401 AUTH_TICKET_INVALID를 반환한다")
  void rejectsMissingTicket() {
    expectTicketInvalid(webTestClient.get().uri(PATH).exchange());
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 이미 사용한 티켓이면 401 AUTH_TICKET_INVALID를 반환한다")
  void rejectsReusedTicket() {
    String ticket = TestTokens.ticket(userId, "USER");
    webTestClient.get().uri(PATH + "?ticket={t}", ticket).exchange().expectStatus().isOk();

    expectTicketInvalid(webTestClient.get().uri(PATH + "?ticket={t}", ticket).exchange());
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 만료된 티켓이면 401 AUTH_TICKET_INVALID를 반환한다")
  void rejectsExpiredTicket() {
    // 허용 오차 30초를 넘겨 만료시킨다.
    String ticket =
        TestTokens.ticketWith(
            userId,
            b -> b.expirationTime(TestTokens.toDate(Instant.now().minusSeconds(31))),
            "USER");

    expectTicketInvalid(webTestClient.get().uri(PATH + "?ticket={t}", ticket).exchange());
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 접근 토큰을 티켓으로 내면 401 AUTH_TICKET_INVALID를 반환한다")
  void rejectsAccessTokenAsTicket() {
    String accessToken = TestTokens.valid(userId, "USER");

    expectTicketInvalid(webTestClient.get().uri(PATH + "?ticket={t}", accessToken).exchange());
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 서명이 맞지 않는 티켓이면 401 AUTH_TICKET_INVALID를 반환한다")
  void rejectsForgedTicket() {
    String ticket = TestTokens.ticketSignedWith(TestTokens.FORGED_KEY);

    expectTicketInvalid(webTestClient.get().uri(PATH + "?ticket={t}", ticket).exchange());
  }

  @Test
  @DisplayName("[SPEC-001 R-28] 티켓을 다른 경로의 접근 토큰으로 쓰면 401 AUTH_TOKEN_INVALID를 반환한다")
  void rejectsTicketAsAccessToken() {
    GatewayErrorTest.expectProblem(
        webTestClient
            .get()
            .uri("/api/v1/media/videos")
            .headers(h -> h.setBearerAuth(TestTokens.ticket(userId, "USER")))
            .exchange(),
        HttpStatus.UNAUTHORIZED,
        "AUTH_TOKEN_INVALID");
  }

  private static void expectTicketInvalid(ResponseSpec response) {
    GatewayErrorTest.expectProblem(response, HttpStatus.UNAUTHORIZED, "AUTH_TICKET_INVALID");
  }
}
