package com.vlm.quality.export.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.export.domain.ExportStatus;
import com.vlm.quality.export.domain.FeatureExport;
import com.vlm.quality.support.ContractSchemas;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.ExportFixtures;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StorageFixtures;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class FeatureExportFlowTest {

  private static final String FEATURE_ROW = "quality/v1/feature-row.schema.json";
  // 시드 7·비율 0.2에서 clip-0001은 eval, clip-0002는 dev다(DatasetSplitterTest).
  private static final long SEED = 7;

  @Autowired FeatureExportService exports;
  @Autowired ExportFixtures fixtures;
  @Autowired DatasetFixtures datasets;
  @Autowired DatasetImportService imports;
  @Autowired GoldenFixtures golden;
  @Autowired ObjectMapper json;

  private final UUID operator = UUID.randomUUID();

  @BeforeEach
  void resetMedia() {
    TestcontainersConfiguration.media().resetAll();
  }

  @Test
  @DisplayName("[SPEC-030 R-14] Golden Set으로 고르면 최신 확정 버전 영상의 위험 상태마다 그 세트 분할을 단 줄을 쓴다")
  void exportsGoldenSet() throws Exception {
    DatasetFixtures.upload("exp", DatasetFixtures.manifest("exp", "clip-0001", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);
    DatasetFixtures.mediaAnswers("clip-0002", 201);
    DatasetImport imported = imports.get(datasets.importNow("exp", 0.2, SEED, operator));
    fixtures.completed(DatasetFixtures.videoIdOf("clip-0001"), 2);
    fixtures.completed(DatasetFixtures.videoIdOf("clip-0002"), 3);

    UUID exportId =
        exports
            .request(
                ExportScope.of(Objects.requireNonNull(imported.evalSetId()), null, null), operator)
            .exportId();
    fixtures.runWorker();

    FeatureExport done = exports.get(exportId);
    assertThat(done.status()).isEqualTo(ExportStatus.COMPLETED);
    assertThat(done.fileKey()).isEqualTo("exports/" + exportId + "/features.jsonl");
    assertThat(done.rowCount()).isEqualTo(2);
    assertThat(done.sourceCount()).isEqualTo(1);
    assertThat(done.finishedAt()).isNotNull();
    List<JsonNode> rows = lines(done);
    assertThat(rows).hasSize(2);
    assertThat(rows)
        .allSatisfy(
            row -> {
              assertThat(row.get("sourceId").asText())
                  .isEqualTo(DatasetFixtures.videoIdOf("clip-0001").toString());
              assertThat(row.get("split").asText()).isEqualTo("eval");
              assertThat(ContractSchemas.validate(FEATURE_ROW, row)).isEmpty();
            });
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 기간으로 고르면 그 UTC 날짜에 받은 영상을 split null로 쓰고 결과 파일이 없는 영상은 건너뛴다")
  void exportsPeriod() throws Exception {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    fixtures.completed(first, 1);
    fixtures.completed(second, 2);
    fixtures.completedWithoutFile(UUID.randomUUID());
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    UUID exportId = exports.request(ExportScope.of(null, today, today), operator).exportId();
    fixtures.runWorker();

    FeatureExport done = exports.get(exportId);
    assertThat(done.status()).isEqualTo(ExportStatus.COMPLETED);
    assertThat(done.rowCount()).isEqualTo(3);
    assertThat(done.sourceCount()).isEqualTo(2);
    assertThat(lines(done))
        .allSatisfy(row -> assertThat(row.get("split").isNull()).isTrue())
        .extracting(row -> row.get("sourceId").asText())
        .containsOnly(first.toString(), second.toString());
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 기간 밖에 받은 영상과 확정 버전이 없는 Golden Set은 줄이 없다")
  void emptyScopes() throws Exception {
    fixtures.completed(UUID.randomUUID(), 1);
    LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    UUID draftOnly = golden.create("draft-only");

    UUID period = exports.request(ExportScope.of(null, yesterday, yesterday), operator).exportId();
    UUID set = exports.request(ExportScope.of(draftOnly, null, null), operator).exportId();
    fixtures.runWorker();

    for (UUID exportId : List.of(period, set)) {
      FeatureExport done = exports.get(exportId);
      assertThat(done.status()).isEqualTo(ExportStatus.COMPLETED);
      assertThat(done.rowCount()).isZero();
      assertThat(done.sourceCount()).isZero();
      assertThat(lines(done)).isEmpty();
    }
  }

  private List<JsonNode> lines(FeatureExport done) throws Exception {
    String body =
        new String(
            StorageFixtures.exportObject(Objects.requireNonNull(done.fileKey())),
            StandardCharsets.UTF_8);
    List<JsonNode> rows = new ArrayList<>();
    for (String line : body.lines().toList()) {
      if (!line.isBlank()) {
        rows.add(json.readTree(line));
      }
    }
    return rows;
  }
}
