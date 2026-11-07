package com.vlm.quality.stage.application;

import com.vlm.quality.stage.domain.StageGateCalculator;
import com.vlm.quality.stage.domain.StageSample;
import com.vlm.quality.stage.domain.StageTimingRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 처리 단계 성능 Gate(SPEC-030 R-16~R-18). 결과를 저장하지 않는다. */
@Service
@RequiredArgsConstructor
public class StageGateService {

  private final StageTimingRepository timings;
  private final StageProperties properties;
  private final Clock clock;

  @Transactional(readOnly = true)
  public StageGateReport evaluate(StageGateCommand command) {
    // 원본 보존 기간(90일) 안의 기록만 남아 있으므로 같은 기간을 읽는다.
    List<StageSample> found =
        timings.p95BySources(
            command.baselineSourceIds(),
            command.candidateSourceIds(),
            command.stageIds(),
            clock.instant().minus(properties.retention()));
    return StageGateReport.of(
        StageGateCalculator.evaluate(
            withRequestedStages(found, command.stageIds()),
            command.maxRegressionRatio(),
            command.minSamples()));
  }

  // 요청한 단계에 기록이 없으면 개수 0으로 넣는다. 그래야 응답에서 빠진 단계와 skipped를 구분할 수 있다.
  private static List<StageSample> withRequestedStages(
      List<StageSample> found, List<String> stageIds) {
    if (stageIds.isEmpty()) {
      return found;
    }
    Map<String, StageSample> byStage =
        found.stream().collect(Collectors.toMap(StageSample::stageId, Function.identity()));
    List<StageSample> samples = new ArrayList<>(found);
    stageIds.stream()
        .distinct()
        .filter(stageId -> !byStage.containsKey(stageId))
        .forEach(stageId -> samples.add(new StageSample(stageId, 0, null, 0, null)));
    samples.sort(Comparator.comparing(StageSample::stageId));
    return List.copyOf(samples);
  }
}
