package com.vlm.quality.dataset.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.dataset.domain.ImportError;
import com.vlm.quality.dataset.domain.ImportErrorCodes;
import com.vlm.quality.golden.application.DatasetBatch.DatasetOrigin;
import com.vlm.quality.shared.domain.ContractDocuments;
import com.vlm.quality.shared.domain.ContractDocuments.ParsedDocument;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 가져오기 매니페스트를 읽고 확인한다(SPEC-030 R-2, NFR-1).
 *
 * <p>JSON을 한 번 읽어 계약 스키마로 한 번 검증한 뒤, 계약이 말로만 정한 규칙(지원 taxonomy, 데이터셋 안에서 유일한 externalId)을 본다.
 */
@Component
@RequiredArgsConstructor
class ImportManifestReader {

  static final String SCHEMA = "quality/v1/dataset-import-manifest.schema.json";
  // 계약 DatasetImport.errors maxItems
  private static final int MAX_ERRORS = 100;

  private final ContractDocuments contracts;
  private final DatasetProperties properties;

  /** 읽은 결과. */
  sealed interface Result permits Invalid, Valid {}

  /** 계약·규칙 위반. 영상을 등록하지 않는다. */
  record Invalid(List<ImportError> errors) implements Result {
    Invalid {
      errors = List.copyOf(errors);
    }
  }

  /** 등록할 매니페스트. */
  record Valid(String datasetName, DatasetOrigin origin, List<ManifestVideo> videos, int labelCount)
      implements Result {
    Valid {
      videos = List.copyOf(videos);
    }
  }

  /** 매니페스트 영상 하나. 라벨은 JSON 그대로 둔다. */
  record ManifestVideo(
      String externalId, String path, String contentType, String labelsJson, int labelCount) {}

  Result read(byte[] manifest) {
    ParsedDocument parsed = contracts.parse(SCHEMA, manifest, MAX_ERRORS);
    JsonNode document = parsed.document();
    if (document == null || !parsed.violations().isEmpty()) {
      return new Invalid(
          parsed.violations().stream()
              .map(v -> new ImportError(null, ImportErrorCodes.MANIFEST_INVALID, v))
              .toList());
    }
    String taxonomy = document.get("taxonomyVersion").asText();
    if (!properties.supportedTaxonomyVersions().contains(taxonomy)) {
      return new Invalid(
          List.of(
              new ImportError(
                  null,
                  ImportErrorCodes.TAXONOMY_UNSUPPORTED,
                  "taxonomyVersion " + taxonomy + "는 지원하지 않는다")));
    }
    List<ManifestVideo> videos = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Set<String> duplicated = new LinkedHashSet<>();
    int labels = 0;
    for (JsonNode video : document.get("videos")) {
      String externalId = video.get("externalId").asText();
      if (!seen.add(externalId)) {
        duplicated.add(externalId);
      }
      JsonNode labelNodes = video.get("labels");
      labels += labelNodes.size();
      videos.add(
          new ManifestVideo(
              externalId,
              video.get("path").asText(),
              video.get("contentType").asText(),
              labelNodes.toString(),
              labelNodes.size()));
    }
    // 계약 설명: externalId는 데이터셋 안에서 유일하다. 같은 ID면 videoId가 겹쳐 한 영상이 덮인다.
    if (!duplicated.isEmpty()) {
      return new Invalid(
          duplicated.stream()
              .limit(MAX_ERRORS)
              .map(
                  id ->
                      new ImportError(
                          id, ImportErrorCodes.MANIFEST_INVALID, "externalId " + id + "가 둘 이상이다"))
              .toList());
    }
    JsonNode origin = document.get("origin");
    return new Valid(
        document.get("datasetName").asText(),
        new DatasetOrigin(origin.get("name").asText(), origin.get("license").asText(), url(origin)),
        videos,
        labels);
  }

  private static @Nullable String url(JsonNode origin) {
    JsonNode url = Objects.requireNonNull(origin.get("url"));
    return url.isNull() ? null : url.asText();
  }
}
