package com.vlm.media.application;

import com.vlm.media.application.NormalizationOutcome.Ready;
import com.vlm.media.application.NormalizationOutcome.Rejected;
import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** normalize가 보낸 정규화 이벤트를 영상 상태에 반영한다(SPEC-007 R-15~R-20). */
@Service
@RequiredArgsConstructor
public class NormalizationEventService {

  private static final Logger log = LoggerFactory.getLogger(NormalizationEventService.class);

  private final VideoRepository videoRepository;
  private final ProcessingEstimate estimate;
  private final Clock clock;

  /** {@code video.normalizing}을 반영한다. */
  @Transactional
  public void onNormalizing(UUID sourceId) {
    lock(sourceId).ifPresent(Video::startNormalizing);
  }

  /** {@code video.normalized}를 반영한다. 전이 조건에 맞지 않으면 아무것도 바꾸지 않는다(R-18). */
  @Transactional
  public void onNormalized(NormalizationOutcome outcome) {
    lock(outcome.sourceId()).ifPresent(video -> apply(video, outcome));
  }

  private void apply(Video video, NormalizationOutcome outcome) {
    if (video.requestStorageCleanupAgain()) {
      log.info(
          "normalization finished after deletion, cleaning storage again: {}", video.getPublicId());
      return;
    }
    switch (outcome) {
      case Ready ready ->
          video.completeNormalization(
              ready.result(), estimate.seconds(ready.result().durationMs()), clock.instant());
      case Rejected rejected ->
          video.rejectNormalization(
              rejected.normalizationId(), rejected.version(), rejected.reason(), clock.instant());
    }
  }

  // 같은 영상의 이벤트와 API 요청(완료·삭제)을 행 잠금으로 한 번에 하나씩 처리한다. 같은 이벤트가 여러 번 와도
  // 뒤에 온 것은 앞이 바꾼 상태를 보고 아무것도 하지 않는다(SPEC-007 NFR-6).
  private Optional<Video> lock(UUID sourceId) {
    Optional<Video> video = videoRepository.findByPublicIdForUpdate(sourceId);
    if (video.isEmpty()) {
      log.warn("normalization event for unknown video: {}", sourceId);
    }
    return video;
  }
}
