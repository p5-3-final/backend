package com.vlm.quality.review.api;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.review.application.ReviewAssignmentService;
import com.vlm.quality.review.application.ReviewItemViewPage;
import com.vlm.quality.review.application.ReviewLabelService;
import com.vlm.quality.review.application.ReviewQueryService;
import com.vlm.quality.review.domain.ReviewFilter;
import com.vlm.quality.review.domain.ReviewReason;
import com.vlm.quality.review.domain.ReviewStatus;
import com.vlm.quality.shared.api.PageMetas;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.util.Arrays;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.ReviewApi;
import vlm.contracts.quality.model.ListReviewItems200Response;
import vlm.contracts.quality.model.ReviewItem;
import vlm.contracts.quality.model.ReviewLabel;

/** 검토 큐 API(계약 tag review). 역할 검사는 {@code OperatorRoleInterceptor}가 먼저 한다(R-4). */
@RestController
@RequiredArgsConstructor
class ReviewItemController implements ReviewApi {

  private final ReviewQueryService queries;
  private final ReviewAssignmentService assignments;
  private final ReviewLabelService labels;
  private final ReviewItemMapper mapper;

  @Override
  public ResponseEntity<ListReviewItems200Response> listReviewItems(
      UUID xUserId,
      String xUserRoles,
      @Nullable String status,
      @Nullable String reason,
      @Nullable String eventType,
      Integer page,
      Integer size) {
    var filter = new ReviewFilter(status(status), reason(reason), eventType(eventType));
    ReviewItemViewPage found = queries.list(filter, page, size);
    return ResponseEntity.ok(
        new ListReviewItems200Response(
            found.items().stream().map(mapper::toResponse).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  @Override
  public ResponseEntity<ReviewItem> getReviewItem(UUID xUserId, String xUserRoles, UUID itemId) {
    return ResponseEntity.ok(mapper.toResponse(queries.get(itemId)));
  }

  @Override
  public ResponseEntity<ReviewItem> assignReviewItem(UUID xUserId, String xUserRoles, UUID itemId) {
    return ResponseEntity.ok(mapper.toResponse(assignments.assign(itemId, xUserId)));
  }

  @Override
  public ResponseEntity<Void> releaseReviewItem(UUID xUserId, String xUserRoles, UUID itemId) {
    assignments.release(itemId, xUserId);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<ReviewItem> labelReviewItem(
      UUID xUserId, String xUserRoles, UUID itemId, ReviewLabel reviewLabel) {
    return ResponseEntity.ok(
        mapper.toResponse(labels.label(itemId, xUserId, ReviewItemMapper.toDomain(reviewLabel))));
  }

  // 생성 인터페이스는 열린 목록(reason)과 taxonomy 참조(eventType)를 문자열로 받으므로 여기서 계약 값인지 본다.
  private static @Nullable ReviewStatus status(@Nullable String value) {
    if (value == null) {
      return null;
    }
    return Arrays.stream(ReviewStatus.values())
        .filter(s -> s.name().equals(value))
        .findFirst()
        .orElseThrow(ReviewItemController::invalid);
  }

  private static @Nullable ReviewReason reason(@Nullable String value) {
    return value == null
        ? null
        : ReviewReason.fromWire(value).orElseThrow(ReviewItemController::invalid);
  }

  private static @Nullable String eventType(@Nullable String value) {
    if (value == null) {
      return null;
    }
    try {
      return ReviewItem.EventTypeEnum.fromValue(value).getValue();
    } catch (IllegalArgumentException e) {
      throw new BusinessException(QualityErrorCode.INVALID_REQUEST, "모르는 eventType입니다.", e);
    }
  }

  private static BusinessException invalid() {
    return new BusinessException(QualityErrorCode.INVALID_REQUEST);
  }
}
