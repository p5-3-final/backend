package com.vlm.quality.export.application;

import com.vlm.quality.export.domain.SourceResult;
import com.vlm.quality.export.domain.SourceResultRepository;
import com.vlm.quality.shared.domain.DeletedSources;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 영상별 마지막 결과 파일 경로를 기록한다(SPEC-030 R-13). */
@Service
@RequiredArgsConstructor
public class SourceResultService {

  private final SourceResultRepository results;
  private final DeletedSources deletedSources;
  private final Clock clock;

  @Transactional
  public void record(SourceCompleted event) {
    // SPEC-029 R-16: 지운 영상의 늦은 완료 이벤트로 기록을 되살리지 않는다.
    if (deletedSources.isDeleted(event.sourceId())) {
      return;
    }
    results.upsert(
        new SourceResult(
            event.sourceId(), event.jobId(), event.revision(), event.resultKey(), clock.instant()));
  }
}
