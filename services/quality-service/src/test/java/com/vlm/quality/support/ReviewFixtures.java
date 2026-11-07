package com.vlm.quality.support;

import com.vlm.quality.review.application.AnalyzedEvent;
import com.vlm.quality.review.application.DismissedAlert;
import com.vlm.quality.review.application.ReviewAssignmentService;
import com.vlm.quality.review.application.ReviewIntakeService;
import com.vlm.quality.review.application.ReviewLabelService;
import com.vlm.quality.shared.domain.ReviewLabel;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 검토 항목을 Kafka 없이 바로 큐에 넣는다. 소비 경로는 EventAnalyzedIntakeTest가 따로 확인한다. */
@Component
public class ReviewFixtures {

  public static final String ANALYSIS =
      "{\"eventType\":\"fall\",\"eventDetected\":true,\"verificationStatus\":\"confirmed\","
          + "\"description\":\"작업자가 넘어졌다.\",\"beforeState\":null,\"afterState\":null,"
          + "\"riskDetected\":true,\"riskType\":\"fall\",\"riskReason\":\"쓰러짐\","
          + "\"solution\":\"확인\",\"severity\":\"high\",\"evidenceStart\":1.0,\"evidenceEnd\":2.0}";

  public static final ReviewLabel CORRECT =
      new ReviewLabel("correct", true, "fall", 1_000L, 4_000L, "high", "넘어짐 맞음");

  private final ReviewIntakeService intake;
  private final ReviewAssignmentService assignments;
  private final ReviewLabelService labels;

  public ReviewFixtures(
      ReviewIntakeService intake, ReviewAssignmentService assignments, ReviewLabelService labels) {
    this.intake = intake;
    this.assignments = assignments;
    this.labels = labels;
  }

  /** 맡아서 라벨까지 붙인 항목을 넣는다. */
  public UUID labeled(UUID sourceId, List<String> frameKeys, ReviewLabel label) {
    UUID itemId = reviewRequired(sourceId, "fall", frameKeys);
    UUID operator = UUID.randomUUID();
    assignments.assign(itemId, operator);
    labels.label(itemId, operator, label);
    return itemId;
  }

  public UUID labeled(UUID sourceId) {
    return labeled(sourceId, List.of(frameKey(sourceId, 0)), CORRECT);
  }

  /** reviewRequired 항목을 넣고 itemId를 돌려준다. */
  public UUID reviewRequired(UUID sourceId, String eventType, List<String> frameKeys) {
    return reviewRequired(sourceId, eventType, frameKeys, 1_000, 5_000);
  }

  /** 영상 시각을 정한 reviewRequired 항목. */
  public UUID reviewRequired(UUID sourceId, long startMs, long endMs) {
    return reviewRequired(sourceId, "fall", List.of(frameKey(sourceId, 0)), startMs, endMs);
  }

  private UUID reviewRequired(
      UUID sourceId, String eventType, List<String> frameKeys, long startMs, long endMs) {
    UUID eventId = UUID.randomUUID();
    intake.intake(
        new AnalyzedEvent(
            eventId,
            sourceId,
            UUID.randomUUID(),
            eventType,
            startMs,
            endMs,
            "vector+vlm+escalate",
            "v2",
            true,
            List.of("judgment_disagreement"),
            "confirmed",
            null,
            frameKeys,
            ANALYSIS));
    return ReviewIntakeService.itemIdOf(eventId);
  }

  public UUID reviewRequired(UUID sourceId) {
    return reviewRequired(sourceId, "fall", List.of(frameKey(sourceId, 0)));
  }

  /** 기각된 긴급 알림(사유 alert_dismissed) 항목을 넣고 itemId를 돌려준다(R-28). 판정 결과가 없다. */
  public UUID dismissedAlert(UUID sourceId) {
    UUID eventId = UUID.randomUUID();
    intake.intakeDismissedAlert(
        new DismissedAlert(
            eventId,
            sourceId,
            UUID.randomUUID(),
            "fall",
            3_000,
            "rejected",
            "v2",
            List.of(frameKey(sourceId, 0))));
    return ReviewIntakeService.itemIdOf(eventId);
  }

  /** 기각된 긴급 알림 항목을 맡아 라벨까지 붙인다. */
  public UUID labeledDismissedAlert(UUID sourceId) {
    UUID itemId = dismissedAlert(sourceId);
    UUID operator = UUID.randomUUID();
    assignments.assign(itemId, operator);
    labels.label(itemId, operator, CORRECT);
    return itemId;
  }

  /** unverified 항목을 넣고 itemId를 돌려준다(R-27). */
  public UUID unverified(UUID sourceId, String reason) {
    UUID eventId = UUID.randomUUID();
    intake.intake(
        new AnalyzedEvent(
            eventId,
            sourceId,
            UUID.randomUUID(),
            "fall",
            1_000,
            5_000,
            "vector+vlm",
            "v2",
            false,
            List.of(),
            "unverified",
            reason,
            List.of(frameKey(sourceId, 0)),
            ANALYSIS.replace("\"confirmed\"", "\"unverified\"")));
    return ReviewIntakeService.itemIdOf(eventId);
  }

  public static String frameKey(UUID sourceId, int index) {
    UUID id =
        UUID.nameUUIDFromBytes(
            sourceId.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return Events.frameKey(sourceId, id.toString(), id.toString(), id.toString(), index);
  }
}
