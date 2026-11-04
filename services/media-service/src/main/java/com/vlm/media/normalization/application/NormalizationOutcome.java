package com.vlm.media.normalization.application;

import com.vlm.media.shared.domain.NormalizationResult;
import com.vlm.media.shared.domain.RejectReason;
import java.util.UUID;

/** 받은 {@code video.normalized}의 결과(SPEC-007 R-16, R-17). */
public sealed interface NormalizationOutcome {

  UUID sourceId();

  /** 정규화 성공. */
  record Ready(UUID sourceId, NormalizationResult result) implements NormalizationOutcome {}

  /** 정규화 거절. */
  record Rejected(UUID sourceId, UUID normalizationId, String version, RejectReason reason)
      implements NormalizationOutcome {}
}
