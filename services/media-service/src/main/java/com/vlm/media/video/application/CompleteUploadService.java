package com.vlm.media.video.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.UploadStorage;
import com.vlm.media.shared.domain.UploadStorage.UploadedPart;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.shared.domain.VideoStatus;
import com.vlm.media.video.application.event.VideoUploaded;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
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
  public Video complete(UUID videoId, UUID userId, List<UploadedPart> parts) {
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
    if (video.isMultipart()) {
      assembleParts(video, parts);
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

  // SPEC-003 R-8: 조각을 합친 뒤 단일 업로드와 같은 크기 확인을 한다. 합치기가 실패해도 바로 거절하지 않는다.
  // 앞선 요청이 저장소에서 합치기를 끝낸 뒤 DB 커밋 전에 실패했을 수 있어서, 파일이 이미 있으면 크기 확인으로 넘어간다.
  private void assembleParts(Video video, List<UploadedPart> parts) {
    if (parts.isEmpty()) {
      throw new BusinessException(MediaErrorCode.UPLOAD_INCOMPLETE);
    }
    String uploadId = Objects.requireNonNull(video.getMultipartUploadId());
    uploadStorage.completeMultipartUpload(video.getStorageKey(), uploadId, parts);
  }
}
