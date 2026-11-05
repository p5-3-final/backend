package com.vlm.auth.token.api;

import com.vlm.auth.token.application.SseTicketService;
import com.vlm.auth.token.domain.SseTicketIssuer.IssuedSseTicket;
import com.vlm.common.web.UserHeaders;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class SseTicketController {

  private final SseTicketService sseTicketService;

  @PostMapping("/api/v1/auth/sse-tickets")
  @ResponseStatus(HttpStatus.CREATED)
  SseTicketResponse issue(
      @RequestHeader(value = UserHeaders.USER_ID, required = false) @Nullable String userId) {
    IssuedSseTicket ticket = sseTicketService.issue(UserHeaders.userId(userId));
    return new SseTicketResponse(ticket.value(), ticket.expiresInSeconds());
  }
}
