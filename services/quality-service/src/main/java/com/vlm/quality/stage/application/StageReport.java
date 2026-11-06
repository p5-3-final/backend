package com.vlm.quality.stage.application;

import com.vlm.quality.stage.domain.StageStat;
import java.util.List;
import java.util.UUID;

/** 영상 하나의 단계 분해(계약 SourceStageReport). */
public record StageReport(UUID sourceId, long recordCount, List<StageStat> stages) {

  public StageReport {
    stages = List.copyOf(stages);
  }
}
