package com.vlm.media.application;

import com.vlm.common.error.BusinessException;
import com.vlm.media.domain.MediaErrorCode;
import com.vlm.media.domain.UploadStorage;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoAccess;
import com.vlm.media.domain.VideoRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaybackService {

  // SPEC-006 R-2: 재생·내려받기 주소는 발급 시점부터 1시간 동안만 유효하다.
  private static final Duration URL_TTL = Duration.ofHours(1);

  private final VideoRepository videoRepository;
  private final UploadStorage storage;
  private final Clock clock;

  /**
   * 원본을 저장소에서 바로 읽는 재생·내려받기 주소를 발급한다(SPEC-006 R-1).
   *
   * @throws BusinessException 없거나 삭제됐거나 볼 권한이 없으면 {@link MediaErrorCode#VIDEO_NOT_FOUND}
   */
  public Playback issue(UUID videoId, UUID userId, boolean admin) {
    Video video =
        videoRepository
            .findByPublicId(videoId)
            .filter(v -> VideoAccess.canView(v, userId, admin))
            .orElseThrow(() -> new BusinessException(MediaErrorCode.VIDEO_NOT_FOUND));
    Instant expiresAt = clock.instant().plus(URL_TTL);
    return new Playback(
        storage.presignRead(video.getStorageKey(), video.getContentType(), null, URL_TTL),
        storage.presignRead(
            video.getStorageKey(), video.getContentType(), video.getFileName(), URL_TTL),
        expiresAt);
  }

  /** 재생 주소, 내려받기 주소, 두 주소의 만료 시각. */
  public record Playback(String url, String downloadUrl, Instant expiresAt) {}
}
