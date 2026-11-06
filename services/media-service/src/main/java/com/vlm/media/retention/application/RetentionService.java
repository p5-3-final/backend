package com.vlm.media.retention.application;

import com.vlm.common.error.BusinessException;
import com.vlm.media.retention.domain.RetainedVideo;
import com.vlm.media.retention.domain.RetainedVideoRepository;
import com.vlm.media.retention.domain.RetentionSetting;
import com.vlm.media.retention.domain.RetentionSettingRepository;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
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

  /**
   * 영상의 학습용 보존 동의를 바꾼다(SPEC-018 R-6). 영상을 지울 때의 값이 적용되므로 이벤트는 내지 않는다.
   *
   * @throws BusinessException 영상이 없으면 {@link MediaErrorCode#VIDEO_NOT_FOUND}
   */
  @Transactional
  public Video changeConsent(UUID videoId, UUID userId, boolean consent) {
    Video video =
        videoRepository
            .findByPublicIdForUpdate(videoId)
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
