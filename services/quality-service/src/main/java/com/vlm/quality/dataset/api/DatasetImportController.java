package com.vlm.quality.dataset.api;

import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.ImportCounts;
import com.vlm.quality.golden.application.Page;
import com.vlm.quality.shared.api.PageMetas;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.DatasetsApi;
import vlm.contracts.quality.model.CreateDatasetImportRequest;
import vlm.contracts.quality.model.DatasetImportCounts;
import vlm.contracts.quality.model.DatasetImportErrorsInner;
import vlm.contracts.quality.model.DatasetImportGoldenSets;
import vlm.contracts.quality.model.ListDatasetImports200Response;

/** 외부 데이터셋 가져오기 API(계약 tag datasets, SPEC-030 R-1, R-9). */
@RestController
@RequiredArgsConstructor
class DatasetImportController implements DatasetsApi {

  private final DatasetImportService imports;

  @Override
  public ResponseEntity<vlm.contracts.quality.model.DatasetImport> createDatasetImport(
      UUID xUserId, String xUserRoles, CreateDatasetImportRequest request) {
    DatasetImport accepted =
        imports.request(
            request.getManifestKey(),
            request.getEvalRatio().doubleValue(),
            request.getSplitSeed(),
            xUserId);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response(accepted));
  }

  @Override
  public ResponseEntity<vlm.contracts.quality.model.DatasetImport> getDatasetImport(
      UUID xUserId, String xUserRoles, UUID importId) {
    return ResponseEntity.ok(response(imports.get(importId)));
  }

  @Override
  public ResponseEntity<ListDatasetImports200Response> listDatasetImports(
      UUID xUserId, String xUserRoles, Integer page, Integer size) {
    Page<DatasetImport> found = imports.list(page, size);
    return ResponseEntity.ok(
        new ListDatasetImports200Response(
            found.items().stream().map(DatasetImportController::response).toList(),
            PageMetas.of(page, size, found.totalElements())));
  }

  static vlm.contracts.quality.model.DatasetImport response(DatasetImport value) {
    ImportCounts counts = value.counts();
    UUID devSetId = value.devSetId();
    UUID evalSetId = value.evalSetId();
    // 계약 goldenSets는 두 세트 ID가 모두 필수다. 확정까지 가지 못한 가져오기는 null이다.
    DatasetImportGoldenSets goldenSets =
        devSetId == null || evalSetId == null
            ? null
            : new DatasetImportGoldenSets(
                devSetId, evalSetId, value.devVersion(), value.evalVersion());
    return new vlm.contracts.quality.model.DatasetImport(
        value.importId(),
        value.datasetName(),
        value.manifestKey(),
        BigDecimal.valueOf(value.evalRatio()),
        Math.toIntExact(value.splitSeed()),
        vlm.contracts.quality.model.DatasetImport.StatusEnum.fromValue(value.status().name()),
        new DatasetImportCounts(
            counts.videos(),
            counts.registered(),
            counts.failed(),
            counts.labels(),
            counts.dev(),
            counts.eval()),
        goldenSets,
        value.errors().stream()
            .map(e -> new DatasetImportErrorsInner(e.externalId(), e.code(), e.message()))
            .toList(),
        value.requestedBy(),
        offset(value.requestedAt()),
        offset(value.finishedAt()));
  }

  private static @Nullable OffsetDateTime offset(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
