package com.vlm.media.retention.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.media.retention.application.event.VideoRetentionRevoked;
import com.vlm.media.retention.domain.RetainedVideo;
import com.vlm.media.retention.domain.RetainedVideoPage;
import com.vlm.media.retention.domain.RetainedVideoRepository;
import com.vlm.media.retention.domain.RetentionSetting;
import com.vlm.media.retention.domain.RetentionSettingRepository;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.shared.domain.VideoStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetentionService {

  private final RetentionSettingRepository settingRepository;
  private final RetainedVideoRepository retainedVideoRepository;
  private final RetentionKeys retentionKeys;
  private final VideoRepository videoRepository;
  private final OutboxRecorder outbox;
  private final Clock clock;

  /**
   * 영상의 학습용 보존 동의를 바꾼다(SPEC-018 R-6). 영상을 지울 때의 값이 적용되므로 이벤트는 내지 않는다.
   *
   * @throws BusinessException 영상이 없거나 삭제됐거나 요청자 소유가 아니면 {@link MediaErrorCode#VIDEO_NOT_FOUND}
   */
  @Transactional
  public Video changeConsent(UUID videoId, UUID userId, boolean consent) {
    Video video =
        videoRepository
            .findByPublicIdForUpdate(videoId)
            // 동의는 소유자의 결정이라 ADMIN도 바꿀 수 없다(SPEC-018 R-7). 볼 수 없는 이유는 구분하지 않는다.
            .filter(v -> v.getStatus() != VideoStatus.DELETED && v.isOwnedBy(userId))
            .orElseThrow(() -> new BusinessException(MediaErrorCode.VIDEO_NOT_FOUND));
    video.changeRetentionConsent(consent);
    return video;
  }

  /**
   * 동의한 채 지운 영상의 최소 기록을 남기고 보존 정보를 돌려준다(SPEC-018 R-8, R-9). 영상 삭제와 같은 트랜잭션에서 불러야 기록과 이벤트가 함께 남는다.
   */
  @Transactional
  public Retention retain(Video video, Instant deletedAt) {
    RetainedVideo record =
        retainedVideoRepository.save(
            RetainedVideo.of(video.getPublicId(), video.getOwnerId(), deletedAt));
    return new Retention(retentionKeys.keyOf(record.getSourceId()), record.getRetainUntil());
  }

  /**
   * 지운 영상의 보존 동의를 철회한다(SPEC-018 R-14). 보존 키를 다시 계산해 철회 이벤트를 기록하고 최소 기록을 지운다. 기록을 잠근 채 처리하므로 동시에 와도
   * 이벤트는 한 번이다(NFR-3).
   *
   * @throws BusinessException 보존 중인 기록이 없거나 요청자 소유가 아니면 {@link
   *     MediaErrorCode#RETAINED_VIDEO_NOT_FOUND}
   */
  @Transactional
  public void revoke(UUID videoId, UUID userId) {
    RetainedVideo record =
        retainedVideoRepository
            .findBySourceIdForUpdate(videoId)
            // 소유자만 철회한다. 남의 기록과 없는 기록을 구분하지 않는다(SPEC-018 R-15).
            .filter(found -> found.getOwnerId().equals(userId))
            .orElseThrow(() -> new BusinessException(MediaErrorCode.RETAINED_VIDEO_NOT_FOUND));
    recordRevoked(record);
  }

  /** 요청자의 보존 중인 삭제 영상을 모두 철회한다. 기록마다 철회 이벤트를 하나씩 남긴다(SPEC-018 R-16). */
  @Transactional
  public void revokeAll(UUID userId) {
    retainedVideoRepository.findAllByOwnerForUpdate(userId).forEach(this::recordRevoked);
  }

  private void recordRevoked(RetainedVideo record) {
    VideoRetentionRevoked event =
        VideoRetentionRevoked.of(
            record.getSourceId(),
            record.getOwnerId(),
            retentionKeys.keyOf(record.getSourceId()),
            clock.instant());
    outbox.record(event.eventId(), VideoRetentionRevoked.TYPE, record.getSourceId(), event);
    retainedVideoRepository.delete(record);
  }

  /** 요청자의 보존 중인 삭제 영상을 삭제 최신순으로 한 페이지 돌려준다(SPEC-018 R-13). */
  @Transactional(readOnly = true)
  public RetainedVideoPage listRetained(UUID ownerId, int page, int size) {
    return retainedVideoRepository.findByOwner(ownerId, page, size);
  }

  /** 이후에 만드는 영상의 기본 동의 값을 바꾼다. 이미 있는 영상의 동의는 그대로다(SPEC-018 R-2). */
  @Transactional
  public boolean updateDefaultConsent(UUID ownerId, boolean defaultConsent) {
    settingRepository.upsert(ownerId, defaultConsent);
    return defaultConsent;
  }

  /** 계정의 기본 동의 값. 바꾼 적이 없으면 false다(SPEC-018 R-1, ADR-0011 "기본값은 보존하지 않음"). */
  @Transactional(readOnly = true)
  public boolean defaultConsent(UUID ownerId) {
    return settingRepository
        .findByOwner(ownerId)
        .map(RetentionSetting::isDefaultConsent)
        .orElse(false);
  }
}
