package com.vlm.quality.review.application;

import com.vlm.quality.shared.domain.ReviewLabel;
import java.util.UUID;

/**
 * Golden Set 초안에 넣을 수 있는 검토 항목(R-12).
 *
 * @param reviewRowId 검토 항목 내부 행 ID(초안 항목 FK)
 * @param label 지금의 라벨. 초안은 이 값을 복사해 둔다
 */
public record GoldenCandidate(
    long reviewRowId, UUID itemId, UUID sourceId, UUID eventId, ReviewLabel label) {}
