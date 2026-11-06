package com.vlm.quality.alert.domain;

import java.util.List;
import java.util.UUID;

/** 후보별 긴급 알림 기록. */
public interface AlertEventRepository {

  /** pending 발행 시각을 남긴다. 이미 남았으면 그대로 둔다. */
  void recordPending(AlertRecord pending);

  /**
   * confirmed·dismissed 결과를 남긴다. 이미 판정이 기록됐으면 그대로 둔다.
   *
   * @return 이번에 판정을 기록했으면 true
   */
  boolean recordResolution(AlertRecord resolution);

  int deleteBySource(UUID sourceId);

  /** 발행 날짜(UTC, 발행 기록이 없으면 판정 날짜)별 지표. 기록이 없는 날은 빠진다. */
  List<AlertDay> report(AlertReportQuery query);
}
