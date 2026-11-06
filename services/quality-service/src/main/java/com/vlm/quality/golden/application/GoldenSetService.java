package com.vlm.quality.golden.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.golden.domain.GoldenSetRepository;
import com.vlm.quality.golden.domain.GoldenSetVersion;
import com.vlm.quality.golden.domain.GoldenVersionRepository;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Golden Set 만들기·조회·기준 실행 기록(SPEC-029 R-11, R-19). */
@Service
@RequiredArgsConstructor
public class GoldenSetService {

  private final GoldenSetRepository sets;
  private final GoldenVersionRepository versions;
  private final Clock clock;

  @Transactional
  public GoldenSetView create(String name, String split, @Nullable String description) {
    UUID setId = UUID.randomUUID();
    if (!sets.insertIfNameFree(setId, name, split, description, clock.instant())) {
      throw new BusinessException(QualityErrorCode.GOLDEN_SET_DUPLICATED);
    }
    return view(setId);
  }

  @Transactional(readOnly = true)
  public Page<GoldenSetView> list(int page, int size) {
    return new Page<>(sets.list(page, size).stream().map(GoldenSetView::of).toList(), sets.count());
  }

  @Transactional(readOnly = true)
  public GoldenSetView view(UUID setId) {
    return GoldenSetView.of(get(setId));
  }

  /**
   * Golden Set을 찾는다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#GOLDEN_SET_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  GoldenSet get(UUID setId) {
    return sets.findBySetId(setId)
        .orElseThrow(() -> new BusinessException(QualityErrorCode.GOLDEN_SET_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public Page<GoldenSetVersion> listVersions(UUID setId, int page, int size) {
    GoldenSet set = get(setId);
    return new Page<>(versions.list(set.id(), setId, page, size), versions.count(set.id()));
  }

  /** 그 Golden Set 버전이 확정돼 있는지(R-17). */
  @Transactional(readOnly = true)
  public boolean versionExists(UUID setId, int version) {
    return versions.exists(setId, version);
  }

  /** 기준 실행을 기록한다. 같은 Golden Set의 실행인지는 부르는 쪽(evaluation)이 확인한다(R-19). */
  @Transactional
  public GoldenSetView setBaseline(UUID setId, UUID runId) {
    get(setId);
    sets.setBaseline(setId, runId, clock.instant());
    return view(setId);
  }
}
