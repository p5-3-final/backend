package com.vlm.quality.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.dataset.application.ImportWorker;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 가져오기 매니페스트·media 대역·작업자 실행(SPEC-030). */
@Component
public class DatasetFixtures {

  public static final String MEDIA_PATH = "/internal/v1/media/dataset-videos";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final DatasetImportService imports;
  private final ImportWorker worker;

  public DatasetFixtures(DatasetImportService imports, ImportWorker worker) {
    this.imports = imports;
    this.worker = worker;
  }

  /** media가 그 영상에 돌려줄 videoId. 테스트가 결과를 맞춰 보려고 externalId로 정한다. */
  public static UUID videoIdOf(String externalId) {
    return UUID.nameUUIDFromBytes(externalId.getBytes(StandardCharsets.UTF_8));
  }

  /** 라벨 3종이 든 영상 하나를 가진 매니페스트. */
  public static ObjectNode manifest(String datasetName, String... externalIds) {
    ObjectNode manifest = JSON.createObjectNode();
    manifest.put("schemaVersion", 1);
    manifest.put("datasetName", datasetName);
    ObjectNode origin = manifest.putObject("origin");
    origin.put("name", "PhysicalAI-Event-Videos");
    origin.put("license", "CC BY 4.0");
    origin.putNull("url");
    manifest.put("taxonomyVersion", "v2");
    ArrayNode videos = manifest.putArray("videos");
    for (String externalId : externalIds) {
      ObjectNode video = videos.addObject();
      video.put("externalId", externalId);
      video.put("path", "videos/" + externalId + ".mp4");
      video.put("contentType", "video/mp4");
      ArrayNode labels = video.putArray("labels");
      ObjectNode interval = labels.addObject();
      interval.put("kind", "interval");
      interval.put("eventType", "ppe_missing");
      interval.put("startMs", 12000);
      interval.put("endMs", 20000);
      interval.put("severity", "medium");
      ObjectNode bbox = labels.addObject();
      bbox.put("kind", "bbox");
      bbox.put("ptsMs", 15000);
      bbox.put("className", "no_helmet");
      ObjectNode box = bbox.putObject("box");
      box.put("x", 0.4);
      box.put("y", 0.2);
      box.put("w", 0.06);
      box.put("h", 0.1);
      bbox.put("trackExternalId", "p1");
      ObjectNode change = labels.addObject();
      change.put("kind", "state_change");
      change.put("atMs", 30000);
      change.put("className", "safety_barrier");
      change.put("change", "removed");
      change.putNull("zoneName");
    }
    return manifest;
  }

  public static String manifestKey(String folder) {
    return "imports/" + folder + "/manifest.json";
  }

  public static void upload(String folder, ObjectNode manifest) {
    upload(folder, manifest.toString().getBytes(StandardCharsets.UTF_8));
  }

  public static void upload(String folder, byte[] manifest) {
    StorageFixtures.putImport(manifestKey(folder), manifest);
  }

  /** media가 그 영상을 status로 답하게 한다. 2xx면 {@link #videoIdOf} 영상을 돌려준다. */
  public static void mediaAnswers(String externalId, int status) {
    var response = aResponse().withStatus(status);
    if (status / 100 == 2) {
      response =
          response
              .withHeader("Content-Type", "application/json")
              .withBody(
                  "{\"videoId\":\""
                      + videoIdOf(externalId)
                      + "\",\"status\":\"UPLOADED\",\"sizeBytes\":1000}");
    } else {
      response =
          response
              .withHeader("Content-Type", "application/problem+json")
              .withBody(
                  "{\"type\":\"about:blank\",\"title\":\"x\",\"status\":"
                      + status
                      + ",\"code\":\"MEDIA_IMPORT_OBJECT_NOT_FOUND\"}");
    }
    TestcontainersConfiguration.media()
        .stubFor(
            post(MEDIA_PATH)
                .withRequestBody(containing("\"externalId\":\"" + externalId + "\""))
                .willReturn(response));
  }

  /** 가져오기를 접수하고 작업자를 한 번 돌린다. */
  public UUID importNow(String folder, double evalRatio, long splitSeed, UUID operator) {
    UUID importId = imports.request(manifestKey(folder), evalRatio, splitSeed, operator).importId();
    runWorker();
    return importId;
  }

  public void runWorker() {
    // 맡을 가져오기가 없을 때까지 돈다.
    boolean worked = true;
    while (worked) {
      worked = worker.runOnce();
    }
  }
}
