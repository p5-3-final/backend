package com.vlm.media.application;

import com.vlm.media.domain.UploadPlan;
import com.vlm.media.domain.UploadStorage;
import com.vlm.media.domain.UploadStorage.MultipartUploadTarget;
import com.vlm.media.domain.UploadStorage.UploadTarget;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateVideoService {

  private final VideoRepository videoRepository;
  private final UploadStorage uploadStorage;
  private final UploadSettings settings;
  private final Clock clock;

  /** 업로드 자리를 만든다(SPEC-003 R-1~R-6). */
  @Transactional
  public CreatedVideo create(UUID ownerId, String fileName, long sizeBytes, String contentType) {
    Instant now = clock.instant();
    Video video =
        Video.startUpload(ownerId, fileName, sizeBytes, contentType, now, settings.uploadExpiry());
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
