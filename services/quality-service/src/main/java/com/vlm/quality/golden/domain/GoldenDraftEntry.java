package com.vlm.quality.golden.domain;

import com.vlm.quality.shared.domain.ReviewLabel;
import java.time.Instant;
import java.util.UUID;

/** 초안 항목. 라벨은 넣을 때의 복사본이다(R-12). */
public record GoldenDraftEntry(
    UUID entryId,
    UUID setId,
    UUID reviewItemId,
    UUID sourceId,
    UUID eventId,
    ReviewLabel label,
    Instant addedAt) {}
