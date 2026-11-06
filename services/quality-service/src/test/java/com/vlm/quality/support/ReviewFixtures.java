package com.vlm.quality.support;

import com.vlm.quality.review.application.AnalyzedEvent;
import com.vlm.quality.review.application.ReviewIntakeService;
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

  private final ReviewIntakeService intake;

  public ReviewFixtures(ReviewIntakeService intake) {
    this.intake = intake;
  }

  /** reviewRequired 항목을 넣고 itemId를 돌려준다. */
  public UUID reviewRequired(UUID sourceId, String eventType, List<String> frameKeys) {
    UUID eventId = UUID.randomUUID();
    intake.intake(
        new AnalyzedEvent(
            eventId,
            sourceId,
            UUID.randomUUID(),
            eventType,
            1_000,
            5_000,
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
