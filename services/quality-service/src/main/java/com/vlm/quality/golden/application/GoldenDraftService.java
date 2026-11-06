package com.vlm.quality.golden.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.golden.domain.GoldenDraftEntry;
import com.vlm.quality.golden.domain.GoldenDraftRepository;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.review.application.GoldenCandidate;
import com.vlm.quality.review.application.ReviewQueryService;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Golden Set 초안 넣기·빼기(SPEC-029 R-12, R-13). 확정 버전은 바꾸지 않는다. */
@Service
@RequiredArgsConstructor
public class GoldenDraftService {

  private final GoldenSetService sets;
  private final GoldenDraftRepository drafts;
  private final ReviewQueryService reviews;
  private final Clock clock;

  @Transactional
  public GoldenDraftEntry add(UUID setId, UUID reviewItemId) {
    GoldenSet set = sets.get(setId);
    if (!GoldenSet.ORIGIN_REVIEW.equals(set.origin())) {
      throw new BusinessException(QualityErrorCode.GOLDEN_ORIGIN_MISMATCH);
    }
    GoldenCandidate candidate = reviews.goldenCandidate(reviewItemId);
    UUID entryId = UUID.randomUUID();
    Instant now = clock.instant();
    if (!drafts.insertIfAbsent(
        entryId, set.id(), candidate.reviewRowId(), candidate.label(), now)) {
      throw new BusinessException(QualityErrorCode.GOLDEN_ENTRY_DUPLICATED);
    }
    return new GoldenDraftEntry(
        entryId,
        setId,
        candidate.itemId(),
        candidate.sourceId(),
        candidate.eventId(),
        candidate.label(),
        now);
  }

  @Transactional(readOnly = true)
  public Page<GoldenDraftEntry> list(UUID setId, int page, int size) {
    GoldenSet set = sets.get(setId);
    return new Page<>(drafts.list(set.id(), page, size), drafts.count(set.id()));
  }

  @Transactional
  public void remove(UUID setId, UUID entryId) {
    GoldenSet set = sets.get(setId);
    if (drafts.remove(set.id(), entryId) == 0) {
      throw new BusinessException(QualityErrorCode.GOLDEN_ENTRY_NOT_FOUND);
    }
  }
}
