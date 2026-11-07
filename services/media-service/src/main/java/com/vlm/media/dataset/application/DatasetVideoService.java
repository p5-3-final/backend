package com.vlm.media.dataset.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.id.NameBasedUuid;
import com.vlm.common.messaging.OutboxRecorder;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.UploadStorage;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import com.vlm.media.video.application.event.VideoUploaded;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 외부 데이터셋 영상 등록(SPEC-030 R-5, R-6).
 *
 * <p>순서: 이미 있으면 그대로 돌려줌 → 가져오기 버킷 크기 확인 → 형식·크기 검사 → 서버 쪽 복사 → 영상 행과 {@code video.uploaded}
 * Outbox(한 트랜잭션). 복사는 길어 트랜잭션 밖에서 한다. 같은 videoId는 같은 대상 경로라 복사를 다시 해도 결과가 같다.
 */
@Service
@RequiredArgsConstructor
public class DatasetVideoService {

  /** 데이터셋 영상 ID 네임스페이스. 바꾸면 이미 등록한 영상과 ID가 달라지므로 바꾸지 않는다(R-5). */
  static final UUID DATASET_VIDEO_NAMESPACE =
      UUID.fromString("8d3c2f4a-6b1e-5a7d-9c0f-2e4b6a8d1c35");

  private static final Logger log = LoggerFactory.getLogger(DatasetVideoService.class);

  private final DatasetVideoProperties properties;
  private final VideoRepository videos;
  private final UploadStorage storage;
  private final OutboxRecorder outbox;
  private final TransactionTemplate transactions;
  private final Clock clock;

  /** (importId, externalId)로 정한 영상 ID. importId는 소문자 문자열로 해시해 호출자의 표기와 무관하게 같다. */
  public static UUID videoIdOf(UUID importId, String externalId) {
    return NameBasedUuid.v5(
        DATASET_VIDEO_NAMESPACE, importId.toString().toLowerCase(Locale.ROOT) + ":" + externalId);
  }

  /**
   * 영상을 등록한다.
   *
   * @throws BusinessException 파일이 없으면 {@link MediaErrorCode#IMPORT_OBJECT_NOT_FOUND}, 크기·형식 위반이면
   *     400 코드
   */
  public RegisteredDatasetVideo register(RegisterDatasetVideoCommand command) {
    UUID videoId = videoIdOf(command.importId(), command.externalId());
    Optional<Video> existing = videos.findByPublicId(videoId);
    if (existing.isPresent()) {
      return new RegisteredDatasetVideo(existing.get(), false);
    }
    OptionalLong size = storage.importObjectSize(command.importKey());
    if (size.isEmpty()) {
      throw new BusinessException(MediaErrorCode.IMPORT_OBJECT_NOT_FOUND);
    }
    Instant now = clock.instant();
    Duration remaining = remaining(command, now);
    Video video =
        Video.registerDataset(
            videoId,
            command.ownerId(),
            fileName(command.importKey()),
            size.getAsLong(),
            command.contentType(),
            now);
    try {
      storage.copyFromImport(command.importKey(), video.getStorageKey(), remaining);
    } catch (UploadStorage.CopyTimeoutException e) {
      throw new BusinessException(
          MediaErrorCode.DEADLINE_EXCEEDED, MediaErrorCode.DEADLINE_EXCEEDED.defaultMessage(), e);
    }
    try {
      Video saved = transactions.execute(status -> save(video, now));
      log.info(
          "Registered dataset video videoId={} importId={} externalId={}",
          videoId,
          command.importId(),
          command.externalId());
      return new RegisteredDatasetVideo(Objects.requireNonNull(saved), true);
    } catch (DataIntegrityViolationException e) {
      // 같은 요청이 동시에 와서 다른 요청이 먼저 저장했다(public_id 고유). 진 쪽은 되돌아가 이벤트도 남지 않는다.
      Video winner = videos.findByPublicId(videoId).orElseThrow(() -> new IllegalStateException(e));
      return new RegisteredDatasetVideo(winner, false);
    }
  }

  // 내부 호출 규약: 남은 시간이 없으면 일을 시작하지 않고 504, 있으면 그 시간을 복사 제한 시간으로 넘긴다.
  private Duration remaining(RegisterDatasetVideoCommand command, Instant now) {
    Instant deadline =
        command.deadline() == null ? now.plus(properties.defaultTimeout()) : command.deadline();
    Duration remaining = Duration.between(now, deadline);
    if (remaining.isNegative() || remaining.isZero()) {
      throw new BusinessException(MediaErrorCode.DEADLINE_EXCEEDED);
    }
    return remaining;
  }

  private Video save(Video video, Instant now) {
    // IDENTITY 키라 save가 바로 INSERT한다. 고유 제약 충돌은 여기서 DataIntegrityViolationException으로 난다.
    Video saved = videos.save(video);
    VideoUploaded event = VideoUploaded.of(saved, now);
    outbox.record(event.eventId(), VideoUploaded.TYPE, saved.getPublicId(), event);
    return saved;
  }

  // 표시용 파일 이름(255자 이하). 저장소 경로는 영상 ID로만 만든다(SPEC-003 R-6).
  private static String fileName(String importKey) {
    String name = importKey.substring(importKey.lastIndexOf('/') + 1);
    return name.length() <= 255 ? name : name.substring(name.length() - 255);
  }
}
