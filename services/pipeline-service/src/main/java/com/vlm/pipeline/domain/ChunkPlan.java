package com.vlm.pipeline.domain;

import java.math.BigDecimal;

/** 소유 구간은 시작 포함·끝 제외이며 확장 구간은 디코딩할 범위를 나타낸다. */
public record ChunkPlan(
    int ordinal,
    BigDecimal ownedStart,
    BigDecimal ownedEnd,
    BigDecimal expandedStart,
    BigDecimal expandedEnd) {
  public ChunkPlan {
    ownedStart = ownedStart.stripTrailingZeros();
    ownedEnd = ownedEnd.stripTrailingZeros();
    expandedStart = expandedStart.stripTrailingZeros();
    expandedEnd = expandedEnd.stripTrailingZeros();
  }
}
