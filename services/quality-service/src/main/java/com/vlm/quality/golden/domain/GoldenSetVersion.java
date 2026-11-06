package com.vlm.quality.golden.domain;

import java.time.Instant;
import java.util.UUID;

/** 확정 버전(R-14). 만든 뒤 바꾸지 않는다(ADR-0011). */
public record GoldenSetVersion(
    UUID setId, int version, int entryCount, String manifestKey, Instant frozenAt, UUID frozenBy) {}
