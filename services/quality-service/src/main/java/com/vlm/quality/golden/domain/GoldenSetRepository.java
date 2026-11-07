package com.vlm.quality.golden.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Golden Set 저장소. */
public interface GoldenSetRepository {

  /** 이름이 없을 때만 만든다(R-11). 만들었으면 true. */
  boolean insertIfNameFree(
      UUID setId, String name, String split, @Nullable String description, Instant now);

  Optional<GoldenSet> findBySetId(UUID setId);

  /** 이름이 없을 때만 데이터셋 세트(origin import)를 만든다(SPEC-030 R-8). 만들었으면 true. */
  boolean insertDatasetSetIfNameFree(UUID setId, String name, String split, Instant now);

  Optional<GoldenSet> findByName(String name);

  /** 데이터셋 세트의 출처·사용 조건을 바꾼다(R-10). */
  void setDatasetOrigin(long goldenSetRowId, String originJson, Instant now);

  List<GoldenSet> list(int page, int size);

  long count();

  void setBaseline(UUID setId, UUID runId, Instant now);

  /**
   * 확정 진행 표시를 건다. 표시가 없거나 staleBefore보다 오래됐을 때만 바꾼다(R-15).
   *
   * @return 바꾼 행 수
   */
  int startFreeze(UUID setId, Instant now, Instant staleBefore);

  void clearFreeze(UUID setId, Instant now);

  /** 확정을 끝낸다: 최신 버전을 바꾸고 표시를 지운다. */
  void finishFreeze(UUID setId, int version, Instant now);
}
