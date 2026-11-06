package com.vlm.quality.golden.application;

import com.vlm.quality.golden.domain.GoldenSet;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 다른 기능(evaluation)에도 돌려주는 Golden Set 값. */
public record GoldenSetView(
    UUID setId,
    String name,
    String split,
    String origin,
    @Nullable String description,
    int draftEntryCount,
    @Nullable Integer latestVersion,
    @Nullable UUID baselineRunId,
    Instant createdAt) {

  static GoldenSetView of(GoldenSet set) {
    return new GoldenSetView(
        set.setId(),
        set.name(),
        set.split(),
        set.origin(),
        set.description(),
        set.draftEntryCount(),
        set.latestVersion(),
        set.baselineRunId(),
        set.createdAt());
  }
}
