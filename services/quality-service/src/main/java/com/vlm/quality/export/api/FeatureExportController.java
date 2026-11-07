package com.vlm.quality.export.api;

import com.vlm.quality.export.application.FeatureExportService;
import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.export.domain.FeatureExport;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.ExportsApi;
import vlm.contracts.quality.model.CreateFeatureExportRequest;

/** 예측 학습용 특징 내보내기 API(계약 tag exports, SPEC-030 R-14, R-15). */
@RestController
@RequiredArgsConstructor
class FeatureExportController implements ExportsApi {

  private final FeatureExportService exports;

  @Override
  public ResponseEntity<vlm.contracts.quality.model.FeatureExport> createFeatureExport(
      UUID xUserId, String xUserRoles, CreateFeatureExportRequest request) {
    ExportScope scope =
        ExportScope.of(request.getGoldenSetId(), request.getFrom(), request.getTo());
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(response(exports.request(scope, xUserId)));
  }

  @Override
  public ResponseEntity<vlm.contracts.quality.model.FeatureExport> getFeatureExport(
      UUID xUserId, String xUserRoles, UUID exportId) {
    return ResponseEntity.ok(response(exports.get(exportId)));
  }

  static vlm.contracts.quality.model.FeatureExport response(FeatureExport value) {
    return new vlm.contracts.quality.model.FeatureExport(
        value.exportId(),
        vlm.contracts.quality.model.FeatureExport.StatusEnum.fromValue(value.status().name()),
        value.scope().goldenSetId(),
        value.scope().from(),
        value.scope().to(),
        value.fileKey(),
        value.rowCount(),
        value.sourceCount(),
        value.requestedAt().atOffset(ZoneOffset.UTC),
        offset(value.finishedAt()));
  }

  private static @Nullable OffsetDateTime offset(@Nullable Instant instant) {
    return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
  }
}
