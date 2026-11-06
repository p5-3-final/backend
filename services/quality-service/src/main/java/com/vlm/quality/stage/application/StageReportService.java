package com.vlm.quality.stage.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import com.vlm.quality.stage.domain.StageStat;
import com.vlm.quality.stage.domain.StageTimingRepository;
import com.vlm.quality.stage.domain.TrendDay;
import com.vlm.quality.stage.domain.TrendQuery;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 처리 단계 리포트(SPEC-029 R-22, R-24, NFR-4). */
@Service
@RequiredArgsConstructor
public class StageReportService {

  private final StageTimingRepository timings;
  private final StageProperties properties;
  private final Clock clock;

  /**
   * 보존 기간 안의 기록만으로 영상별 단계 분해를 만든다.
   *
   * @throws BusinessException 기록이 없으면 {@link QualityErrorCode#SOURCE_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public StageReport sourceReport(UUID sourceId) {
    List<StageStat> stages =
        timings.sourceReport(sourceId, clock.instant().minus(properties.retention()));
    if (stages.isEmpty()) {
      throw new BusinessException(QualityErrorCode.SOURCE_NOT_FOUND);
    }
    return new StageReport(sourceId, stages.stream().mapToLong(StageStat::count).sum(), stages);
  }

  /** 일 집계에서 읽는다. 기록이 없는 날은 빠진다. */
  @Transactional(readOnly = true)
  public List<TrendDay> trend(TrendQuery query) {
    return timings.trend(query);
  }
}
