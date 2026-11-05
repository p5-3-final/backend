package com.vlm.pipeline.application;

import com.vlm.pipeline.domain.DispatchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DispatchRecoveryService {
  private final DispatchRepository repository;

  /** 기한은 새 실행의 시작 제한일 뿐 실제 종료의 증거가 아니므로 슬롯은 유지한다. */
  @Transactional
  public int markExpired() {
    repository.lock();
    return repository.expire();
  }

  /** 운영자가 실행 종료를 외부에서 확인한 뒤 증거 참조로 반환한다. 성공 결과로 기록하지 않는다. */
  @Transactional
  public void confirmStopped(UUID executionId, UUID confirmationId, String evidence) {
    new com.vlm.pipeline.domain.StopConfirmation(executionId, confirmationId, evidence);
    repository.lock();
    repository.expire();
    repository.confirmStopped(executionId, confirmationId, evidence);
  }

  @Transactional(readOnly = true)
  public long recoveryCount() {
    return repository.recoveryCount();
  }
}
