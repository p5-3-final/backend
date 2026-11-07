package com.vlm.quality.dataset.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 가져오기와 그 영상 저장소. */
public interface DatasetImportRepository {

  /** 같은 매니페스트 경로가 없을 때만 PENDING으로 만든다. 만들었으면 true. */
  boolean insertIfAbsent(
      UUID importId,
      String manifestKey,
      double evalRatio,
      long splitSeed,
      UUID requestedBy,
      Instant now);

  /** 행을 잠그고 읽는다. 같은 매니페스트의 요청을 하나씩 판단한다(R-1). */
  Optional<DatasetImport> lockByManifestKey(String manifestKey);

  /** 끝난 가져오기를 비우고 PENDING으로 되돌린다. 영상 행도 지운다. */
  void restart(long id, double evalRatio, long splitSeed, UUID requestedBy, Instant now);

  Optional<DatasetImport> findByImportId(UUID importId);

  /** 접수 시각 내림차순. */
  List<DatasetImport> list(int page, int size);

  long count();

  /**
   * 처리할 가져오기 하나를 다른 작업자가 잡지 못하게 맡는다. PENDING이거나, 진행 중인데 heartbeat가 staleBefore보다 오래된 행이다.
   *
   * @return 맡은 가져오기. heartbeat를 now로 바꾼 뒤의 값이다
   */
  Optional<DatasetImport> claim(Instant now, Instant staleBefore);

  void heartbeat(long id, Instant now);

  void markValidating(long id, Instant now);

  /** 검증한 영상을 저장하고 REGISTERING으로 바꾼다. 이전 시도의 영상 행은 지운다. */
  void saveValidated(
      long id, String datasetName, List<NewImportVideo> videos, int labelCount, Instant now);

  /** 매니페스트 오류로 끝낸다. 영상은 등록하지 않았다(R-2). */
  void fail(long id, List<ImportError> errors, Instant now);

  /** 등록할 영상(PENDING), 행 순서. */
  List<ImportVideo> pendingVideos(long id);

  /** 등록된 영상, 행 순서. */
  List<ImportVideo> registeredVideos(long id);

  void markRegistered(long id, long videoRowId, UUID sourceId, Instant now);

  /** 영상 하나를 실패로 표시하고, 오류가 100건보다 적으면 오류 목록에 더한다(R-7). */
  void markFailed(long id, long videoRowId, ImportError error, Instant now);

  /** 등록 결과로 끝낸다. 개수(등록·실패·분할)는 영상 행에서 다시 센다. */
  void complete(
      long id,
      ImportStatus status,
      ImportGoldenSets goldenSets,
      List<ImportError> extraErrors,
      Instant now);
}
