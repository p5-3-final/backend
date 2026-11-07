package com.vlm.quality.stage.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** 측정 원본·일 집계 저장소. 대량 삽입·집계라 JDBC로 다룬다. */
public interface StageTimingRepository {

  /** 같은 timingId의 기존 기록을 지우고 새로 넣는다(R-21). 파티션 테이블이라 timingId만으로 유일 제약을 둘 수 없다. */
  void replace(List<StageTiming> timings);

  /** since 이후 그 영상 기록을 경로·단계별로 묶는다(R-22). */
  List<StageStat> sourceReport(UUID sourceId, Instant since);

  /** 하루(UTC) 원본을 집계해 일 집계 표에 upsert한다(R-23). 쓴 행 수. */
  int aggregateDay(LocalDate day);

  List<TrendDay> trend(TrendQuery query);

  /** 월 파티션이 없으면 만든다. */
  void createPartition(YearMonth month);

  /** 상한이 cutoff 이하인 월 파티션을 지운다(R-25). 지운 파티션 이름. */
  List<String> dropPartitionsEndingBefore(Instant cutoff);

  /**
   * since 이후 기준·후보 영상 기록을 단계별로 묶어 개수와 p95를 구한다(SPEC-030 R-16). 음수 durationMs는 뺀다.
   *
   * @param stageIds 비어 있으면 양쪽에 기록이 있는 단계만, 아니면 그 단계 중 기록이 있는 단계
   */
  List<StageSample> p95BySources(
      List<UUID> baselineSourceIds,
      List<UUID> candidateSourceIds,
      List<String> stageIds,
      Instant since);
}
