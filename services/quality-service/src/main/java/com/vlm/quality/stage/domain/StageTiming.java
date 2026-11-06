package com.vlm.quality.stage.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 처리 단계 측정 기록 하나(계약 stages/v1/stage-timing, SPEC-013).
 *
 * @param candidateId subject.candidateId. 긴급 알림 1차 지연(analyzer.alert)을 후보와 잇는다(R-28)
 * @param service 묶음을 보낸 서비스
 * @param durationMs 음수일 수 있다(queue_wait은 다른 Pod 시계를 비교한다)
 * @param versions 결과에 영향을 준 모델·설정 버전
 */
public record StageTiming(
    UUID timingId,
    String stageId,
    String path,
    @Nullable UUID sourceId,
    @Nullable String candidateId,
    String service,
    int attempt,
    Instant startedAt,
    Instant endedAt,
    double durationMs,
    String status,
    Map<String, String> versions) {

  public StageTiming {
    versions = Map.copyOf(versions);
  }
}
