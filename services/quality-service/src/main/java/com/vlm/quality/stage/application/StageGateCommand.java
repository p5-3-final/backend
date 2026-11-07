package com.vlm.quality.stage.application;

import java.util.List;
import java.util.UUID;

/**
 * 성능 Gate 요청(계약 StageGateRequest).
 *
 * @param stageIds 비어 있으면 양쪽에 기록이 있는 모든 단계
 */
public record StageGateCommand(
    List<UUID> baselineSourceIds,
    List<UUID> candidateSourceIds,
    List<String> stageIds,
    double maxRegressionRatio,
    int minSamples) {

  public StageGateCommand {
    baselineSourceIds = List.copyOf(baselineSourceIds);
    candidateSourceIds = List.copyOf(candidateSourceIds);
    stageIds = List.copyOf(stageIds);
  }
}
