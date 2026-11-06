package com.vlm.quality.review.application;

import com.vlm.common.id.NameBasedUuid;
import com.vlm.quality.review.domain.NewReviewItem;
import com.vlm.quality.review.domain.ReviewItemRepository;
import com.vlm.quality.review.domain.ReviewReason;
import com.vlm.quality.review.domain.ReviewSampling;
import com.vlm.quality.shared.domain.DeletedSources;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판정 결과와 기각된 긴급 알림을 검토 큐에 넣는다(SPEC-029 R-1~R-3, R-27, R-28). */
@Service
@RequiredArgsConstructor
public class ReviewIntakeService {

  /** 검토 항목 ID 네임스페이스. 바꾸면 이미 만든 itemId와 달라지므로 바꾸지 않는다(R-1). */
  static final UUID REVIEW_ITEM_NAMESPACE = UUID.fromString("5f0c1a3e-2b7d-5e49-9a61-0c7f3d2b8e41");

  // 판정이 alert.raised에서 왔음을 나타내는 판정 방식. 계약 ReviewItem.judgmentMethod는 열린 문자열이다.
  static final String ALERT_JUDGMENT_METHOD = "alert";
  private static final String EMPTY_ANALYSIS = "{}";
  private static final Set<String> DECIDED = Set.of("confirmed", "rejected");
  private static final String UNVERIFIED = "unverified";

  private static final Logger log = LoggerFactory.getLogger(ReviewIntakeService.class);

  private final ReviewItemRepository items;
  private final DeletedSources deletedSources;
  private final ReviewProperties properties;
  private final Clock clock;

  /** 검토 항목 ID(eventId로 결정적). */
  public static UUID itemIdOf(UUID eventId) {
    return NameBasedUuid.v5(REVIEW_ITEM_NAMESPACE, eventId.toString());
  }

  /**
   * event.analyzed 하나를 처리한다. 큐에 넣을 사유가 없거나 삭제된 영상이면 아무것도 하지 않는다.
   *
   * @return 새 항목을 넣었으면 true
   */
  @Transactional
  public boolean intake(AnalyzedEvent event) {
    Optional<ReviewReason> reason = reasonOf(event);
    if (reason.isEmpty()) {
      return false;
    }
    if (deletedSources.isDeleted(event.sourceId())) {
      log.info("Skipping event.analyzed for deleted source eventId={}", event.eventId());
      return false;
    }
    return items.insertIfAbsent(
        new NewReviewItem(
            itemIdOf(event.eventId()),
            event.eventId(),
            event.sourceId(),
            event.jobId(),
            event.candidateEventType(),
            event.startMs(),
            event.endMs(),
            event.judgmentMethod(),
            event.taxonomyVersion(),
            reason.get(),
            event.reviewReasons(),
            event.evidenceFrameKeys(),
            event.analysisJson(),
            clock.instant()));
  }

  /**
   * 기각된 긴급 알림을 사유 {@code alert_dismissed}로 넣는다(R-28). 같은 알림은 한 번만 들어간다(R-3).
   *
   * @return 새 항목을 넣었으면 true
   */
  @Transactional
  public boolean intakeDismissedAlert(DismissedAlert alert) {
    if (deletedSources.isDeleted(alert.sourceId())) {
      return false;
    }
    return items.insertIfAbsent(
        new NewReviewItem(
            itemIdOf(alert.eventId()),
            alert.eventId(),
            alert.sourceId(),
            alert.jobId(),
            alert.candidateEventType(),
            alert.observedMs(),
            alert.observedMs(),
            ALERT_JUDGMENT_METHOD,
            alert.taxonomyVersion(),
            ReviewReason.ALERT_DISMISSED,
            List.of(alert.dismissReason()),
            alert.evidenceFrameKeys(),
            // 판정 결과가 없다. 계약 ReviewItem.analysis는 null을 허용하지 않아 빈 객체를 둔다.
            EMPTY_ANALYSIS,
            clock.instant()));
  }

  private Optional<ReviewReason> reasonOf(AnalyzedEvent event) {
    if (event.reviewRequired()) {
      return Optional.of(ReviewReason.REVIEW_REQUIRED);
    }
    // R-27: 예산·상한·모델 미설정으로 판정하지 않은 이벤트는 표본 규칙과 무관하게 재판정 큐에 넣는다.
    if (UNVERIFIED.equals(event.verificationStatus()) && event.unverifiedReason() != null) {
      return ReviewReason.fromWire(event.unverifiedReason());
    }
    if (DECIDED.contains(event.verificationStatus())
        && ReviewSampling.sampled(
            event.eventId(), properties.sampleRate(event.candidateEventType()))) {
      return Optional.of(ReviewReason.SAMPLE);
    }
    return Optional.empty();
  }
}
