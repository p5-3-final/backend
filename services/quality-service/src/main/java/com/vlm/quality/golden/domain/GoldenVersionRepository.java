package com.vlm.quality.golden.domain;

import java.util.List;
import java.util.UUID;

/** 확정 버전 저장소. */
public interface GoldenVersionRepository {

  void insert(long goldenSetRowId, GoldenSetVersion version);

  /** 버전 번호 내림차순. */
  List<GoldenSetVersion> list(long goldenSetRowId, UUID setId, int page, int size);

  long count(long goldenSetRowId);

  boolean exists(UUID setId, int version);
}
