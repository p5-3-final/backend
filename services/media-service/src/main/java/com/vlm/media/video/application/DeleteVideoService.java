package com.vlm.media.video.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.media.retention.application.Retention;
import com.vlm.media.retention.application.RetentionService;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoAccess;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.video.application.event.VideoDeleted;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteVideoService {

  private final VideoRepository videoRepository;
  private final OutboxRecorder outbox;
  private final RetentionService retentionService;
  private final Clock clock;

  /**
   * 영상을 삭제 상태로 바꾸고 {@code video.deleted}를 기록한다(SPEC-003 R-18, R-19). 저장소 파일은 {@link
   * StorageCleanupJob}이 지운다.
   *
   * @throws BusinessException 없거나 이미 삭제됐거나 지울 권한이 없으면 {@link MediaErrorCode#VIDEO_NOT_FOUND}
   */
  @Transactional
  public void delete(UUID videoId, UUID userId, boolean admin) {
    // 동시에 온 삭제 요청이 이벤트를 두 번 기록하지 않도록 행을 잠근 뒤 상태를 본다.
    Video video =
        videoRepository
            .findByPublicIdForUpdate(videoId)
            .filter(v -> VideoAccess.canView(v, userId, admin))
            .orElseThrow(() -> new BusinessException(MediaErrorCode.VIDEO_NOT_FOUND));
    Instant now = clock.instant();
    video.delete(now);
    // SPEC-018 R-8, R-10: 동의한 영상만 보존한다. 보존 기록과 이벤트를 상태 변경과 같은 트랜잭션에 넣어
    // 하나만 남는 일이 없게 한다.
    Retention retention = video.isRetentionConsent() ? retentionService.retain(video, now) : null;
    VideoDeleted event = VideoDeleted.of(video, now, retention);
    outbox.record(event.eventId(), VideoDeleted.TYPE, video.getPublicId(), event);
  }
}
