package com.vlm.quality.golden.domain;

import com.vlm.quality.shared.domain.ReviewLabel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Golden Set 초안 항목 저장소. */
public interface GoldenDraftRepository {

  /** 같은 검토 항목이 초안에 없을 때만 넣는다. 넣었으면 true. */
  boolean insertIfAbsent(
      UUID entryId, long goldenSetRowId, long reviewRowId, ReviewLabel label, Instant addedAt);

  List<GoldenDraftEntry> list(long goldenSetRowId, int page, int size);

  long count(long goldenSetRowId);

  /** 빼면 1, 없으면 0. */
  int remove(long goldenSetRowId, UUID entryId);

  /** 확정할 초안 전체. 추가 순이다. */
  List<FreezeEntry> forFreeze(long goldenSetRowId);
}
