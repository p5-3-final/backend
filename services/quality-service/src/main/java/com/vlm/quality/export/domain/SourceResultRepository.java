package com.vlm.quality.export.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 영상별 마지막 결과 파일 기록. */
public interface SourceResultRepository {

  /**
   * 다른 job이면 바꾸고, 같은 job이면 revision이 더 클 때만 바꾼다.
   *
   * @return 기록을 바꿨으면 true
   */
  boolean upsert(SourceResult result);

  List<SourceResult> findBySources(List<UUID> sourceIds);

  List<SourceResult> findReceivedBetween(LocalDate from, LocalDate to);

  int deleteBySource(UUID sourceId);
}
