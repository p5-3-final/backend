package com.vlm.quality.golden.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.support.ContractSchemas;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StorageFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class DatasetGoldenManifestTest {

  @Autowired DatasetFixtures datasets;
  @Autowired DatasetImportService imports;
  @Autowired ObjectMapper json;

  @Test
  @DisplayName(
      "[SPEC-030 R-10] 데이터셋 세트 확정 매니페스트에 origin import·datasetOrigin·항목별 sourceId·externalId·라벨을 담고 계약을 통과한다")
  void writesDatasetManifest() throws Exception {
    TestcontainersConfiguration.media().resetAll();
    var manifest = DatasetFixtures.manifest("manifestds", "clip-0002");
    DatasetFixtures.upload("manifest", manifest);
    DatasetFixtures.mediaAnswers("clip-0002", 201);

    DatasetImport result = imports.get(datasets.importNow("manifest", 0.2, 7, UUID.randomUUID()));

    JsonNode frozen =
        json.readTree(
            StorageFixtures.goldenObject("golden/" + result.devSetId() + "/v1/manifest.json"));
    assertThat(ContractSchemas.validate("quality/v1/golden-manifest.schema.json", frozen))
        .isEmpty();
    assertThat(frozen.get("origin").asText()).isEqualTo("import");
    assertThat(frozen.get("name").asText()).isEqualTo("manifestds-dev");
    assertThat(frozen.get("entries")).isEmpty();
    assertThat(frozen.at("/datasetOrigin/license").asText()).isEqualTo("CC BY 4.0");
    JsonNode item = frozen.get("datasetItems").get(0);
    assertThat(item.get("externalId").asText()).isEqualTo("clip-0002");
    assertThat(item.get("sourceId").asText())
        .isEqualTo(DatasetFixtures.videoIdOf("clip-0002").toString());
    assertThat(item.get("importId").asText()).isEqualTo(result.importId().toString());
    assertThat(item.get("itemId").asText())
        .isEqualTo(GoldenDatasetService.itemIdOf("manifestds", "clip-0002").toString());
    assertThat(item.get("labels")).isEqualTo(manifest.at("/videos/0/labels"));
  }
}
