package com.vlm.media.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.media.application.event.VideoUploaded;
import com.vlm.media.domain.MediaErrorCode;
import com.vlm.media.domain.UploadStorage;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import com.vlm.media.domain.VideoStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompleteUploadService {

  private final VideoRepository videoRepository;
  private final UploadStorage uploadStorage;
  private final OutboxRecorder outbox;
  private final Clock clock;

  /** 업로드가 끝났는지 확인하고 업로드 완료로 바꾼다(SPEC-003 R-7, R-9~R-12). */
  @Transactional
  public Video complete(UUID videoId, UUID userId) {
    // 같은 영상의 완료 요청은 행 잠금으로 한 번에 하나씩 처리한다. 저장소 확인도 잠금 안에서 해서,
    // 뒤에 온 요청은 앞 요청이 바꾼 상태를 보고 아무것도 하지 않는다(SPEC-003 NFR-2, R-11).
    Video video =
        videoRepository
            .findByPublicIdForUpdate(videoId)
            .filter(v -> v.isOwnedBy(userId))
            .orElseThrow(() -> new BusinessException(MediaErrorCode.VIDEO_NOT_FOUND));
    if (video.isUploadCompleted()) {
      return video;
    }
    if (video.getStatus() != VideoStatus.UPLOADING) {
      throw new BusinessException(MediaErrorCode.INVALID_STATE);
    }
    OptionalLong size = uploadStorage.objectSize(video.getStorageKey());
    if (size.isEmpty() || size.getAsLong() != video.getSizeBytes()) {
      throw new BusinessException(MediaErrorCode.UPLOAD_INCOMPLETE);
    }
    Instant now = clock.instant();
    video.completeUpload(now);
    VideoUploaded event = VideoUploaded.of(video, now);
    outbox.record(event.eventId(), VideoUploaded.TYPE, video.getPublicId(), event);
    return video;
  }
}
