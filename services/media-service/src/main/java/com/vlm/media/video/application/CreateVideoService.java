package com.vlm.media.video.application;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.SessionRole;
import com.vlm.media.shared.domain.UploadStorage;
import com.vlm.media.shared.domain.UploadStorage.MultipartUploadTarget;
import com.vlm.media.shared.domain.UploadStorage.UploadTarget;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.shared.domain.WorkSession;
import com.vlm.media.shared.domain.WorkSessionRepository;
import com.vlm.media.video.domain.UploadPlan;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateVideoService {

  private final VideoRepository videoRepository;
  private final WorkSessionRepository sessionRepository;
  private final UploadStorage uploadStorage;
  private final UploadSettings settings;
  private final Clock clock;

  /**
   * 업로드 자리를 만든다(SPEC-003 R-1~R-6). 세션과 역할이 있으면 영상을 그 세션에 넣는다(SPEC-017 R-6).
   *
   * @throws BusinessException 세션이 없으면 {@link MediaErrorCode#SESSION_NOT_FOUND}
   */
  @Transactional
  public CreatedVideo create(
      UUID ownerId,
      String fileName,
      long sizeBytes,
      String contentType,
      @Nullable UUID sessionId,
      @Nullable SessionRole role) {
    Instant now = clock.instant();
    Video video =
        Video.startUpload(ownerId, fileName, sizeBytes, contentType, now, settings.uploadExpiry());
    // 저장소 자리는 세션 확인 뒤에 준비한다. 거절될 요청에 멀티파트 업로드를 만들지 않는다.
    if (sessionId != null && role != null) {
      WorkSession session =
          sessionRepository
              .findByPublicIdForUpdate(sessionId)
              .orElseThrow(() -> new BusinessException(MediaErrorCode.SESSION_NOT_FOUND));
      video.joinSession(session.getPublicId(), role);
    }
    Instant urlExpiresAt = now.plus(settings.urlTtl());
    UploadPlan plan = UploadPlan.of(sizeBytes, settings.singleMaxBytes(), settings.partSizeBytes());
    UploadTarget upload;
    if (plan.method() == UploadPlan.Method.SINGLE) {
      upload =
          uploadStorage.prepareSingleUpload(
              video.getStorageKey(), contentType, sizeBytes, urlExpiresAt);
    } else {
      String uploadId = uploadStorage.startMultipartUpload(video.getStorageKey(), contentType);
      video.useMultipart(uploadId, plan.partSizeBytes());
      upload =
          new MultipartUploadTarget(
              plan.partSizeBytes(),
              uploadStorage.presignParts(
                  video.getStorageKey(), uploadId, plan.partCount(), settings.urlTtl()),
              urlExpiresAt);
    }
    return new CreatedVideo(videoRepository.save(video), upload);
  }
}
