package com.vlm.auth.infra.token;

import com.vlm.auth.domain.SseTicketIssuer;
import com.vlm.auth.domain.User;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 계약 {@code access-token.md}의 "알림 연결 티켓"을 발급한다. 1회용 처리는 api-gateway가 하므로 저장하지 않는다. */
@Component
@RequiredArgsConstructor
class JwtSseTicketIssuer implements SseTicketIssuer {

  static final String AUDIENCE = "vlm-sse";
  // SPEC-001 R-26: 티켓은 30초 동안만 유효하다.
  static final Duration TIME_TO_LIVE = Duration.ofSeconds(30);

  private final UserTokenSigner signer;

  @Override
  public IssuedSseTicket issue(User user) {
    return new IssuedSseTicket(signer.sign(user, AUDIENCE, TIME_TO_LIVE), TIME_TO_LIVE.toSeconds());
  }
}
