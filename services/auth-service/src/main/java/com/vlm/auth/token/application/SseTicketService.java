package com.vlm.auth.token.application;

import com.vlm.auth.shared.domain.AuthErrorCode;
import com.vlm.auth.shared.domain.UserRepository;
import com.vlm.auth.token.domain.SseTicketIssuer;
import com.vlm.auth.token.domain.SseTicketIssuer.IssuedSseTicket;
import com.vlm.common.error.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SseTicketService {

  private final UserRepository userRepository;
  private final SseTicketIssuer sseTicketIssuer;

  // 역할은 헤더가 아니라 DB에서 읽어, 티켓에 현재 역할이 실리게 한다.
  @Transactional(readOnly = true)
  public IssuedSseTicket issue(UUID userPublicId) {
    return userRepository
        .findByPublicId(userPublicId)
        .map(sseTicketIssuer::issue)
        .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_INVALID));
  }
}
