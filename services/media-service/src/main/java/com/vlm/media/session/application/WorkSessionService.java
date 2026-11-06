package com.vlm.media.session.application;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.SessionOrder;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.shared.domain.WorkSession;
import com.vlm.media.shared.domain.WorkSessionRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkSessionService {

  private final WorkSessionRepository sessionRepository;
  private final VideoRepository videoRepository;
  private final Clock clock;

  /** 요청자를 소유자로 하는 세션을 만든다(SPEC-017 R-1). */
  @Transactional
  public WorkSession create(
      UUID ownerId, String name, @Nullable String site, @Nullable String area) {
    return sessionRepository.save(WorkSession.create(ownerId, name, site, area, clock.instant()));
  }

  /**
   * 세션과 속한 "삭제"가 아닌 영상을 세션 안 순서로 돌려준다(SPEC-017 R-4, R-7).
   *
   * @throws BusinessException 세션이 없으면 {@link MediaErrorCode#SESSION_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public SessionDetail get(UUID sessionId, UUID userId, boolean admin) {
    WorkSession session =
        sessionRepository
            .findByPublicId(sessionId)
            .orElseThrow(() -> new BusinessException(MediaErrorCode.SESSION_NOT_FOUND));
    List<Video> videos =
        videoRepository.findVisibleInSession(sessionId).stream()
            .sorted(SessionOrder.COMPARATOR)
            .toList();
    return new SessionDetail(session, videos);
  }

  /** 세션과 세션 안 순서로 놓은 영상. */
  public record SessionDetail(WorkSession session, List<Video> videos) {}
}
