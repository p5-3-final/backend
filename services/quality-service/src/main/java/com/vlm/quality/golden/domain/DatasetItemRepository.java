package com.vlm.quality.golden.domain;

import java.time.Instant;
import java.util.List;

/** 데이터셋 Golden Set 항목 저장소. */
public interface DatasetItemRepository {

  /**
   * item_id로 넣거나 바꾼다. 다시 가져오기에서 분할이 바뀌면 항목이 다른 세트로 옮겨 간다(R-8).
   *
   * @param goldenSetRowId 항목을 둘 세트의 내부 행 ID
   */
  void upsert(long goldenSetRowId, List<DatasetItem> items, Instant now);

  /** 확정할 항목 전체. 넣은 순서다. */
  List<DatasetItem> forFreeze(long goldenSetRowId);
}
