package com.vlm.quality.dataset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.common.error.BusinessException;
import com.vlm.quality.dataset.application.ImportManifestReader.Invalid;
import com.vlm.quality.dataset.application.ImportManifestReader.ManifestVideo;
import com.vlm.quality.dataset.application.ImportManifestReader.Valid;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.DatasetImportRepository;
import com.vlm.quality.dataset.domain.DatasetSplitter;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.Registered;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.Rejected;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.VideoRegistration;
import com.vlm.quality.dataset.domain.ImportError;
import com.vlm.quality.dataset.domain.ImportErrorCodes;
import com.vlm.quality.dataset.domain.ImportFiles;
import com.vlm.quality.dataset.domain.ImportGoldenSets;
import com.vlm.quality.dataset.domain.ImportStatus;
import com.vlm.quality.dataset.domain.ImportVideo;
import com.vlm.quality.dataset.domain.NewImportVideo;
import com.vlm.quality.golden.application.DatasetBatch;
import com.vlm.quality.golden.application.DatasetBatch.DatasetOrigin;
import com.vlm.quality.golden.application.DatasetFreeze;
import com.vlm.quality.golden.application.GoldenDatasetService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 가져오기 하나를 끝까지 처리한다(SPEC-030 R-2~R-4, R-7, R-8).
 *
 * <p>상태마다 DB에 남기므로 Pod가 죽으면 heartbeat가 끊긴 행을 다른 작업자가 이어받는다. VALIDATING이면 검증부터, REGISTERING이면 남은
 * 영상부터 한다. 등록은 media에서 멱등이라 끊긴 영상을 다시 불러도 같은 videoId다.
 */
@Service
@RequiredArgsConstructor
public class ImportWorker {

  private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);

  private final DatasetImportRepository imports;
  private final ImportFiles files;
  private final ImportManifestReader manifests;
  private final DatasetVideoRegistry media;
  private final GoldenDatasetService golden;
  private final DatasetProperties properties;
  private final ObjectMapper json;
  private final Clock clock;

  /**
   * 맡을 가져오기가 있으면 하나를 처리한다.
   *
   * @return 처리했으면 true. 없으면 false
   */
  public boolean runOnce() {
    Instant now = clock.instant();
    Optional<DatasetImport> claimed = imports.claim(now, now.minus(properties.heartbeatTimeout()));
    if (claimed.isEmpty()) {
      return false;
    }
    DatasetImport job = claimed.get();
    log.info("Dataset import started importId={} status={}", job.importId(), job.status());
    if (job.status() != ImportStatus.REGISTERING && !validate(job)) {
      return true;
    }
    register(job);
    finish(imports.findByImportId(job.importId()).orElseThrow(IllegalStateException::new));
    return true;
  }

  private boolean validate(DatasetImport job) {
    imports.markValidating(job.id(), clock.instant());
    Optional<byte[]> manifest = files.read(job.manifestKey());
    if (manifest.isEmpty()) {
      fail(
          job,
          List.of(
              new ImportError(
                  null, ImportErrorCodes.MANIFEST_NOT_FOUND, job.manifestKey() + " 없음")));
      return false;
    }
    ImportManifestReader.Result result = manifests.read(manifest.get());
    if (result instanceof Invalid invalid) {
      fail(job, invalid.errors());
      return false;
    }
    Valid valid = (Valid) result;
    List<NewImportVideo> videos =
        valid.videos().stream().map(video -> splitOf(job, video)).toList();
    imports.saveValidated(
        job.id(),
        valid.datasetName(),
        write(valid.origin()),
        videos,
        valid.labelCount(),
        clock.instant());
    log.info(
        "Dataset import validated importId={} videos={} labels={}",
        job.importId(),
        videos.size(),
        valid.labelCount());
    return true;
  }

  private static NewImportVideo splitOf(DatasetImport job, ManifestVideo video) {
    return new NewImportVideo(
        video.externalId(),
        video.path(),
        video.contentType(),
        DatasetSplitter.split(job.splitSeed(), video.externalId(), job.evalRatio()),
        video.labelsJson(),
        video.labelCount());
  }

  // R-7: 영상 하나가 실패해도 나머지를 계속한다. 영상마다 결과를 남겨 heartbeat도 함께 갱신한다.
  private void register(DatasetImport job) {
    for (ImportVideo video : imports.pendingVideos(job.id())) {
      var result =
          media.register(
              new VideoRegistration(
                  job.importId(),
                  video.externalId(),
                  job.requestedBy(),
                  job.folder() + video.path(),
                  video.contentType()));
      Instant now = clock.instant();
      switch (result) {
        case Registered registered ->
            imports.markRegistered(job.id(), video.id(), registered.videoId(), now);
        case Rejected rejected ->
            imports.markFailed(
                job.id(),
                video.id(),
                new ImportError(video.externalId(), rejected.code(), rejected.message()),
                now);
      }
    }
  }

  private void finish(DatasetImport job) {
    List<ImportVideo> registered = imports.registeredVideos(job.id());
    if (registered.isEmpty()) {
      // R-7: 등록된 영상이 없으면 Golden Set을 만들지 않는다. 영상별 오류가 이유다.
      imports.complete(
          job.id(), ImportStatus.FAILED, ImportGoldenSets.NONE, List.of(), clock.instant());
      log.warn("Dataset import failed importId={} reason=no video registered", job.importId());
      return;
    }
    try {
      DatasetFreeze frozen = golden.importAndFreeze(batch(job, registered));
      imports.complete(
          job.id(),
          ImportStatus.COMPLETED,
          new ImportGoldenSets(
              frozen.devSetId(), frozen.evalSetId(), frozen.devVersion(), frozen.evalVersion()),
          List.of(),
          clock.instant());
      log.info(
          "Dataset import completed importId={} registered={} devVersion={} evalVersion={}",
          job.importId(),
          registered.size(),
          frozen.devVersion(),
          frozen.evalVersion());
    } catch (BusinessException e) {
      // 같은 이름의 검토 라벨 세트가 있거나 확정이 겹쳤다. 다시 해도 같으므로 실패로 끝낸다.
      imports.complete(
          job.id(),
          ImportStatus.FAILED,
          ImportGoldenSets.NONE,
          List.of(
              new ImportError(null, e.getErrorCode().code(), e.getErrorCode().defaultMessage())),
          clock.instant());
      log.warn(
          "Dataset import failed importId={} code={}", job.importId(), e.getErrorCode().code());
    }
  }

  private DatasetBatch batch(DatasetImport job, List<ImportVideo> registered) {
    return new DatasetBatch(
        Objects.requireNonNull(job.datasetName()),
        read(Objects.requireNonNull(job.datasetOriginJson())),
        job.importId(),
        job.requestedBy(),
        registered.stream()
            .map(
                video ->
                    new DatasetBatch.Entry(
                        video.externalId(),
                        Objects.requireNonNull(video.sourceId()),
                        video.split().wire(),
                        video.labelsJson()))
            .toList());
  }

  private void fail(DatasetImport job, List<ImportError> errors) {
    imports.fail(job.id(), errors, clock.instant());
    log.warn(
        "Dataset import failed importId={} code={}",
        job.importId(),
        errors.isEmpty() ? "-" : errors.get(0).code());
  }

  private String write(DatasetOrigin origin) {
    try {
      return json.writeValueAsString(origin);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write dataset origin", e);
    }
  }

  private DatasetOrigin read(String origin) {
    try {
      return json.readValue(origin, DatasetOrigin.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored dataset origin is not JSON", e);
    }
  }
}
