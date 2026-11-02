package com.vlm.auth.application;

import com.vlm.auth.domain.LoginAudit;
import com.vlm.auth.domain.LoginAuditRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class LoginAuditRecorder {

  private final LoginAuditRepository loginAuditRepository;
  private final Clock clock;

  // 로그인 실패는 예외로 끝나 바깥 트랜잭션이 롤백되므로, 기록은 별도 트랜잭션으로 남긴다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(@Nullable Long userId, boolean success, String clientIp) {
    loginAuditRepository.save(LoginAudit.of(userId, success, clientIp, clock.instant()));
  }
}
