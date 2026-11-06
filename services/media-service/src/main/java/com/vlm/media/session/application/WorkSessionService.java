package com.vlm.media.session.application;

import com.vlm.media.shared.domain.WorkSession;
import com.vlm.media.shared.domain.WorkSessionRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkSessionService {

  private final WorkSessionRepository sessionRepository;
  private final Clock clock;

  /** 요청자를 소유자로 하는 세션을 만든다(SPEC-017 R-1). */
  @Transactional
  public WorkSession create(
      UUID ownerId, String name, @Nullable String site, @Nullable String area) {
    return sessionRepository.save(WorkSession.create(ownerId, name, site, area, clock.instant()));
  }
}
