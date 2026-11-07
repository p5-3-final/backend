package com.vlm.quality.dataset.application;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.ImportStatus;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class ImportManifestValidationTest {

  @Autowired DatasetFixtures datasets;
  @Autowired DatasetImportService imports;
  @Autowired JdbcTemplate jdbc;

  private final UUID operator = UUID.randomUUID();

  @BeforeEach
  void resetMedia() {
    TestcontainersConfiguration.media().resetAll();
  }

  @Test
  @DisplayName("[SPEC-030 R-2] 매니페스트 파일이 없으면 영상을 등록하지 않고 FAILED·QUALITY_IMPORT_MANIFEST_NOT_FOUND다")
  void failsWithoutManifest() {
    DatasetImport result = run("missing-manifest");

    assertFailed(result, "QUALITY_IMPORT_MANIFEST_NOT_FOUND");
  }

  @Test
  @DisplayName("[SPEC-030 R-2] JSON이 아니거나 계약을 어기면 FAILED·QUALITY_IMPORT_MANIFEST_INVALID이고 위치를 남긴다")
  void failsOnContractViolation() {
    DatasetFixtures.upload("not-json", "{".getBytes(StandardCharsets.UTF_8));
    ObjectNode broken = DatasetFixtures.manifest("broken", "clip-0001");
    broken.remove("datasetName");
    DatasetFixtures.upload("broken", broken);

    assertFailed(run("not-json"), "QUALITY_IMPORT_MANIFEST_INVALID");
    DatasetImport invalid = run("broken");
    assertFailed(invalid, "QUALITY_IMPORT_MANIFEST_INVALID");
    assertThat(invalid.errors().get(0).message()).startsWith("$");
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-2] 지원하지 않는 taxonomyVersion이면 FAILED·QUALITY_IMPORT_TAXONOMY_UNSUPPORTED다")
  void failsOnUnsupportedTaxonomy() {
    ObjectNode manifest = DatasetFixtures.manifest("old-taxonomy", "clip-0001");
    manifest.put("taxonomyVersion", "v9");
    DatasetFixtures.upload("old-taxonomy", manifest);

    assertFailed(run("old-taxonomy"), "QUALITY_IMPORT_TAXONOMY_UNSUPPORTED");
  }

  @Test
  @DisplayName("[SPEC-030 R-2] 같은 externalId가 둘 이상이면 FAILED·QUALITY_IMPORT_MANIFEST_INVALID다")
  void failsOnDuplicateExternalId() {
    DatasetFixtures.upload("dup", DatasetFixtures.manifest("dup", "clip-0001", "clip-0001"));

    DatasetImport result = run("dup");

    assertFailed(result, "QUALITY_IMPORT_MANIFEST_INVALID");
    assertThat(result.errors().get(0).externalId()).isEqualTo("clip-0001");
  }

  private DatasetImport run(String folder) {
    UUID importId =
        imports.request(DatasetFixtures.manifestKey(folder), 0.2, 0, operator).importId();
    datasets.runWorker();
    return imports.get(importId);
  }

  private void assertFailed(DatasetImport result, String code) {
    assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
    assertThat(result.finishedAt()).isNotNull();
    assertThat(result.errors()).isNotEmpty().allMatch(e -> e.code().equals(code));
    assertThat(result.counts().registered()).isZero();
    assertThat(TestcontainersConfiguration.media().findAll(anyRequestedFor(anyUrl()))).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM dataset_import_videos v JOIN dataset_imports i"
                    + " ON i.id = v.import_id WHERE i.import_id = ?",
                Integer.class,
                result.importId()))
        .isZero();
  }
}
