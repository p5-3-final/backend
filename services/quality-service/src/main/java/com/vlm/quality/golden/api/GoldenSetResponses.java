package com.vlm.quality.golden.api;

import com.vlm.quality.golden.application.GoldenSetView;
import java.time.ZoneOffset;
import vlm.contracts.quality.model.GoldenSet;

/** Golden Set 응답. */
final class GoldenSetResponses {

  private GoldenSetResponses() {}

  static GoldenSet of(GoldenSetView set) {
    return new GoldenSet(
        set.setId(),
        set.name(),
        GoldenSet.SplitEnum.fromValue(set.split()),
        set.description(),
        set.draftEntryCount(),
        set.latestVersion(),
        set.baselineRunId(),
        set.createdAt().atOffset(ZoneOffset.UTC),
        GoldenSet.OriginEnum.fromValue(set.origin()));
  }
}
