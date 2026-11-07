package com.vlm.quality.golden.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.golden.domain.DatasetItem;
import com.vlm.quality.golden.domain.FreezeEntry;
import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.shared.domain.ContractDocuments;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 확정 버전 매니페스트(계약 quality/v1/golden-manifest)를 만든다. 쓰기 전에 계약으로 확인한다. */
@Component
@RequiredArgsConstructor
class GoldenManifestWriter {

  private static final String SCHEMA = "quality/v1/golden-manifest.schema.json";

  private final ObjectMapper json;
  private final ContractDocuments contracts;

  /**
   * 매니페스트 JSON을 만든다.
   *
   * @param frameKeys 항목별로 Golden Set 저장소에 복사한 프레임 경로
   */
  byte[] write(
      GoldenSet set,
      int version,
      Instant frozenAt,
      List<FreezeEntry> entries,
      Map<UUID, List<String>> frameKeys) {
    ObjectNode manifest = header(set, version, frozenAt);
    manifest.put("origin", GoldenSet.ORIGIN_REVIEW);
    ArrayNode items = manifest.putArray("entries");
    for (FreezeEntry entry : entries) {
      ObjectNode item = items.addObject();
      item.put("entryId", entry.entryId().toString());
      item.put("sourceId", entry.sourceId().toString());
      item.put("eventId", entry.eventId().toString());
      item.put("candidateEventType", entry.candidateEventType());
      item.put("judgmentMethod", entry.judgmentMethod());
      item.put("startMs", entry.startMs());
      item.put("endMs", entry.endMs());
      item.set("label", json.valueToTree(entry.label()));
      item.set("analysis", readTree(entry.analysisJson()));
      ArrayNode frames = item.putArray("frameKeys");
      frameKeys.getOrDefault(entry.entryId(), List.of()).forEach(frames::add);
      item.put("taxonomyVersion", entry.taxonomyVersion());
    }
    String document = manifest.toString();
    contracts.requireValid(SCHEMA, document);
    return document.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * 데이터셋 세트(origin import)의 매니페스트를 만든다(SPEC-030 R-10). 검토 항목(entries)은 비어 있다.
   *
   * @param items 확정할 데이터셋 항목
   */
  byte[] writeDataset(GoldenSet set, int version, Instant frozenAt, List<DatasetItem> items) {
    ObjectNode manifest = header(set, version, frozenAt);
    manifest.put("origin", GoldenSet.ORIGIN_IMPORT);
    manifest.putArray("entries");
    ArrayNode datasetItems = manifest.putArray("datasetItems");
    for (DatasetItem item : items) {
      ObjectNode node = datasetItems.addObject();
      node.put("itemId", item.itemId().toString());
      node.put("externalId", item.externalId());
      node.put("sourceId", item.sourceId().toString());
      node.put("importId", item.importId().toString());
      node.set("labels", readJson(item.labelsJson()));
    }
    String origin = set.datasetOriginJson();
    manifest.set("datasetOrigin", origin == null ? json.nullNode() : readJson(origin));
    String document = manifest.toString();
    contracts.requireValid(SCHEMA, document);
    return document.getBytes(StandardCharsets.UTF_8);
  }

  private ObjectNode header(GoldenSet set, int version, Instant frozenAt) {
    ObjectNode manifest = json.createObjectNode();
    manifest.put("schemaVersion", 1);
    manifest.put("setId", set.setId().toString());
    manifest.put("name", set.name());
    manifest.put("split", set.split());
    manifest.put("version", version);
    manifest.put("frozenAt", frozenAt.toString());
    return manifest;
  }

  private JsonNode readJson(String value) {
    try {
      return json.readTree(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored dataset value is not JSON", e);
    }
  }

  private ObjectNode readTree(String value) {
    try {
      return (ObjectNode) json.readTree(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("stored analysis is not JSON", e);
    }
  }
}
