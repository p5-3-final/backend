package com.vlm.quality.export.application;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.export.domain.FeatureExport;
import com.vlm.quality.export.domain.FeatureExportRepository;
import com.vlm.quality.golden.application.GoldenSourceService;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내보내기 접수·조회(SPEC-030 R-14, R-15). 처리는 {@link FeatureExportWorker}가 비동기로 한다. */
@Service
@RequiredArgsConstructor
public class FeatureExportService {

  private static final Logger log = LoggerFactory.getLogger(FeatureExportService.class);

  private final FeatureExportRepository exports;
  private final GoldenSourceService golden;
  private final Clock clock;

  /**
   * 내보내기를 PENDING으로 접수한다.
   *
   * @throws BusinessException 없는 Golden Set이면 {@link QualityErrorCode#GOLDEN_SET_NOT_FOUND}
   */
  @Transactional
  public FeatureExport request(ExportScope scope, UUID operator) {
    UUID goldenSetId = scope.goldenSetId();
    if (goldenSetId != null) {
      golden.requireExists(goldenSetId);
    }
    UUID exportId = UUID.randomUUID();
    exports.insert(exportId, scope, operator, clock.instant());
    log.info("Accepted feature export exportId={} goldenSet={}", exportId, scope.isGoldenSet());
    return get(exportId);
  }

  /**
   * 내보내기 상태를 읽는다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#EXPORT_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public FeatureExport get(UUID exportId) {
    return exports
        .findByExportId(exportId)
        .orElseThrow(() -> new BusinessException(QualityErrorCode.EXPORT_NOT_FOUND));
  }
}
