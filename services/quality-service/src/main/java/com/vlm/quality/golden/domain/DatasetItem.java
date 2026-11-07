package com.vlm.quality.golden.domain;

import java.util.UUID;

/**
 * 데이터셋 Golden Set 항목(계약 golden-manifest {@code datasetItems[]}, SPEC-030 R-8, R-10).
 *
 * @param itemId 데이터셋 이름·externalId로 결정적
 * @param labelsJson 가져오기 매니페스트 라벨 배열 그대로(JSON)
 */
public record DatasetItem(
    UUID itemId, String externalId, UUID sourceId, UUID importId, String labelsJson) {}
