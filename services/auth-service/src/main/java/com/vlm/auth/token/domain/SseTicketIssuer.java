package com.vlm.auth.token.domain;

import com.vlm.auth.shared.domain.User;

/** 알림 연결 티켓 발급. 형식은 docs 레포 {@code service-contracts/auth/access-token.md}. 구현은 infra 레이어에 있다. */
public interface SseTicketIssuer {

  IssuedSseTicket issue(User user);

  /**
   * 발급한 알림 연결 티켓.
   *
   * @param value 서명된 JWT
   * @param expiresInSeconds 만료까지 남은 초
   */
  record IssuedSseTicket(String value, long expiresInSeconds) {}
}
