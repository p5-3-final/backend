package com.vlm.quality.dataset.application;

import com.vlm.common.error.BusinessException;
import com.vlm.common.id.NameBasedUuid;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.DatasetImportRepository;
import com.vlm.quality.golden.application.Page;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 가져오기 접수·조회(SPEC-030 R-1, R-9). 처리는 {@link ImportWorker}가 비동기로 한다. */
@Service
@RequiredArgsConstructor
public class DatasetImportService {

  /** 가져오기 ID 네임스페이스. 바꾸면 같은 매니페스트의 videoId가 달라지므로 바꾸지 않는다(D-30). */
  static final UUID IMPORT_NAMESPACE = UUID.fromString("3a7e9c51-0d4b-5f62-8e1a-6c2b9d4f7a03");

  private static final Logger log = LoggerFactory.getLogger(DatasetImportService.class);

  private final DatasetImportRepository imports;
  private final Clock clock;

  /** 매니페스트 경로로 정한 가져오기 ID. 같은 매니페스트를 다시 가져오면 같은 importId라 media가 같은 videoId를 돌려준다(US-1, D-30). */
  public static UUID importIdOf(String manifestKey) {
    return NameBasedUuid.v5(IMPORT_NAMESPACE, manifestKey);
  }

  /**
   * 가져오기를 접수한다. 처음이면 만들고, 끝난 가져오기면 비우고 다시 PENDING으로 둔다.
   *
   * @throws BusinessException 같은 매니페스트가 진행 중이면 {@link QualityErrorCode#IMPORT_IN_PROGRESS}
   */
  @Transactional
  public DatasetImport request(
      String manifestKey, double evalRatio, long splitSeed, UUID operator) {
    Instant now = clock.instant();
    UUID importId = importIdOf(manifestKey);
    // 동시에 처음 요청이 와도 manifest_key 고유 제약으로 하나만 만든다. 진 쪽은 아래 잠금에서 PENDING을 보고 409다.
    if (!imports.insertIfAbsent(importId, manifestKey, evalRatio, splitSeed, operator, now)) {
      DatasetImport existing =
          imports.lockByManifestKey(manifestKey).orElseThrow(IllegalStateException::new);
      if (existing.status().inProgress()) {
        throw new BusinessException(QualityErrorCode.IMPORT_IN_PROGRESS);
      }
      imports.restart(existing.id(), evalRatio, splitSeed, operator, now);
    }
    log.info("Accepted dataset import importId={} manifestKey={}", importId, manifestKey);
    return get(importId);
  }

  /**
   * 가져오기 상태를 읽는다.
   *
   * @throws BusinessException 없으면 {@link QualityErrorCode#IMPORT_NOT_FOUND}
   */
  @Transactional(readOnly = true)
  public DatasetImport get(UUID importId) {
    return imports
        .findByImportId(importId)
        .orElseThrow(() -> new BusinessException(QualityErrorCode.IMPORT_NOT_FOUND));
  }

  /** 접수 시각 내림차순. */
  @Transactional(readOnly = true)
  public Page<DatasetImport> list(int page, int size) {
    return new Page<>(imports.list(page, size), imports.count());
  }
}
