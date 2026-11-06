package com.vlm.quality.stage.application;

import com.vlm.quality.stage.domain.StageTiming;
import com.vlm.quality.stage.domain.StageTimingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 측정 기록 적재(SPEC-029 R-21, NFR-3). */
@Service
@RequiredArgsConstructor
public class StageTimingIngestService {

  private static final Logger log = LoggerFactory.getLogger(StageTimingIngestService.class);

  private final StageTimingRepository timings;
  private final StageProperties properties;
  private final Clock clock;

  /**
   * 기록마다 timingId로 upsert한다. 보존 기간보다 오래된 기록은 곧 지워질 데이터이고 파티션도 없어 저장하지 않는다.
   *
   * @return 저장한 기록 수
   */
  @Transactional
  public int ingest(StageTimingBatch batch) {
    Instant oldest = clock.instant().minus(properties.retention());
    List<StageTiming> kept =
        batch.timings().stream().filter(t -> !t.startedAt().isBefore(oldest)).toList();
    int skipped = batch.timings().size() - kept.size();
    if (skipped > 0) {
      log.info("Skipping stage timings older than retention count={}", skipped);
    }
    timings.replace(kept);
    return kept.size();
  }
}
