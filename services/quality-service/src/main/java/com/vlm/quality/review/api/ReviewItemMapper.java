package com.vlm.quality.review.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.review.application.ReviewItemView;
import com.vlm.quality.review.domain.ReviewItem;
import com.vlm.quality.review.domain.ReviewLabel;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** 검토 항목을 계약 응답 모델로 옮긴다. */
@Component
@RequiredArgsConstructor
class ReviewItemMapper {

  private final ObjectMapper json;

  vlm.contracts.quality.model.ReviewItem toResponse(ReviewItemView view) {
    ReviewItem item = view.item();
    return new vlm.contracts.quality.model.ReviewItem(
        item.itemId(),
        item.eventId(),
        item.sourceId(),
        item.jobId(),
        vlm.contracts.quality.model.ReviewItem.EventTypeEnum.fromValue(item.eventType()),
        Math.toIntExact(item.startMs()),
        Math.toIntExact(item.endMs()),
        item.judgmentMethod(),
        item.reason().wire(),
        item.reviewReasons(),
        item.evidenceFrameKeys(),
        view.evidenceFrameUrls(),
        analysis(item.analysisJson()),
        vlm.contracts.quality.model.ReviewItem.StatusEnum.fromValue(item.status().name()),
        item.assigneeId(),
        offset(item.assignedAt()),
        toResponse(item.label()),
        offset(item.labeledAt()),
        offset(item.createdAt()));
  }

  static vlm.contracts.quality.model.@Nullable ReviewLabel toResponse(@Nullable ReviewLabel label) {
    if (label == null) {
      return null;
    }
    return new vlm.contracts.quality.model.ReviewLabel(
        vlm.contracts.quality.model.ReviewLabel.VerdictEnum.fromValue(label.verdict()),
        label.eventPresent(),
        label.eventType() == null
            ? null
            : vlm.contracts.quality.model.ReviewLabel.EventTypeEnum.fromValue(label.eventType()),
        label.startMs() == null ? null : Math.toIntExact(label.startMs()),
        label.endMs() == null ? null : Math.toIntExact(label.endMs()),
        label.severity() == null
            ? null
            : vlm.contracts.quality.model.ReviewLabel.SeverityEnum.fromValue(label.severity()),
        label.note());
  }

  static ReviewLabel toDomain(vlm.contracts.quality.model.ReviewLabel label) {
    return new ReviewLabel(
        label.getVerdict().getValue(),
        Boolean.TRUE.equals(label.getEventPresent()),
        label.getEventType() == null ? null : label.getEventType().getValue(),
        label.getStartMs() == null ? null : label.getStartMs().longValue(),
        label.getEndMs() == null ? null : label.getEndMs().longValue(),
        label.getSeverity() == null ? null : label.getSeverity().getValue(),
        label.getNote());
  }

  static @Nullable OffsetDateTime offset(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }

  // 계약 ReviewItem.analysis는 자유 객체다. 키 순서를 지키려고 LinkedHashMap으로 읽는다.
  private Map<String, Object> analysis(String analysisJson) {
    try {
      return json.readValue(
          analysisJson,
          json.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored analysis is not JSON", e);
    }
  }
}
