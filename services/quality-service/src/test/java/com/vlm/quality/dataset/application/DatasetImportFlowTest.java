package com.vlm.quality.dataset.application;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.domain.DatasetImport;
import com.vlm.quality.dataset.domain.DatasetSplitter;
import com.vlm.quality.dataset.domain.ImportStatus;
import com.vlm.quality.dataset.domain.Split;
import com.vlm.quality.golden.application.GoldenDatasetService;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DatasetImportFlowTest {

  // 시드 7에서 clip-0001은 0.104(eval), clip-0002는 0.541(dev)이다(DatasetSplitterTest).
  private static final long SEED = 7;
  private static final double RATIO = 0.2;

  @Autowired DatasetFixtures datasets;
  @Autowired DatasetImportService imports;
  @Autowired GoldenFixtures golden;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  private final UUID operator = UUID.randomUUID();

  @BeforeEach
  void resetMedia() {
    TestcontainersConfiguration.media().resetAll();
  }

  @Test
  @DisplayName("[SPEC-030 R-4] 영상마다 importId·externalId·요청 운영자·매니페스트 폴더 기준 경로로 media를 부른다")
  void callsMediaPerVideo() throws Exception {
    DatasetFixtures.upload("call", DatasetFixtures.manifest("call", "clip-0001"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);

    UUID importId = datasets.importNow("call", RATIO, SEED, operator);

    LoggedRequest sent = mediaRequests().get(0);
    JsonNode body = json.readTree(sent.getBodyAsString());
    assertThat(body.get("importId").asText()).isEqualTo(importId.toString());
    assertThat(body.get("externalId").asText()).isEqualTo("clip-0001");
    assertThat(body.get("ownerId").asText()).isEqualTo(operator.toString());
    assertThat(body.get("importKey").asText()).isEqualTo("imports/call/videos/clip-0001.mp4");
    assertThat(sent.getHeader("X-Caller")).isEqualTo("quality-service");
  }

  @Test
  @DisplayName("[SPEC-030 R-7] 영상 하나가 실패하면 그 영상만 오류에 남기고 나머지를 등록해 COMPLETED다")
  void keepsGoingAfterOneFailure() {
    DatasetFixtures.upload(
        "partial", DatasetFixtures.manifest("partial", "clip-0001", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);
    DatasetFixtures.mediaAnswers("clip-0002", 404);

    DatasetImport result = imports.get(datasets.importNow("partial", RATIO, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
    assertThat(result.counts().videos()).isEqualTo(2);
    assertThat(result.counts().registered()).isEqualTo(1);
    assertThat(result.counts().failed()).isEqualTo(1);
    assertThat(result.counts().labels()).isEqualTo(6);
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).externalId()).isEqualTo("clip-0002");
    assertThat(result.errors().get(0).code()).isEqualTo("QUALITY_IMPORT_VIDEO_NOT_FOUND");
  }

  @Test
  @DisplayName("[SPEC-030 R-7] 등록된 영상이 하나도 없으면 FAILED이고 Golden Set을 만들지 않는다")
  void failsWhenNothingRegistered() {
    DatasetFixtures.upload("none", DatasetFixtures.manifest("none", "clip-0001"));
    DatasetFixtures.mediaAnswers("clip-0001", 404);

    DatasetImport result = imports.get(datasets.importNow("none", RATIO, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
    assertThat(result.devSetId()).isNull();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM golden_sets", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-030 R-7] 실패한 영상이 100건을 넘으면 오류는 처음 100건만 남기고 개수는 모두 센다")
  void keepsFirstHundredErrors() {
    String[] ids = new String[101];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = "clip-" + i;
    }
    DatasetFixtures.upload("many", DatasetFixtures.manifest("many", ids));
    // 등록 요청은 모두 대역 기본 응답(404)이다.

    DatasetImport result = imports.get(datasets.importNow("many", RATIO, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
    assertThat(result.counts().failed()).isEqualTo(101);
    assertThat(result.errors()).hasSize(100);
    assertThat(result.errors().get(0).externalId()).isEqualTo("clip-0");
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-8] 등록된 영상의 라벨을 분할에 맞는 <이름>-dev·<이름>-eval(origin import)에 넣고 각각 버전 1로 확정한다")
  void writesDatasetGoldenSets() {
    DatasetFixtures.upload("split", DatasetFixtures.manifest("splitds", "clip-0001", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);
    DatasetFixtures.mediaAnswers("clip-0002", 201);
    assertThat(DatasetSplitter.split(SEED, "clip-0001", RATIO)).isEqualTo(Split.EVAL);

    DatasetImport result = imports.get(datasets.importNow("split", RATIO, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
    assertThat(result.counts().dev()).isEqualTo(1);
    assertThat(result.counts().eval()).isEqualTo(1);
    assertThat(result.devVersion()).isEqualTo(1);
    assertThat(result.evalVersion()).isEqualTo(1);
    Map<String, Object> dev =
        jdbc.queryForMap(
            "SELECT name, split, origin, latest_version FROM golden_sets WHERE set_id = ?",
            result.devSetId());
    assertThat(dev)
        .containsEntry("name", "splitds-dev")
        .containsEntry("split", "dev")
        .containsEntry("origin", "import")
        .containsEntry("latest_version", 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT source_id FROM golden_dataset_items i JOIN golden_sets g"
                    + " ON g.id = i.golden_set_id WHERE g.set_id = ?",
                UUID.class,
                result.evalSetId()))
        .isEqualTo(DatasetFixtures.videoIdOf("clip-0001"));
  }

  @Test
  @DisplayName("[SPEC-030 R-8] 항목이 없는 분할은 확정하지 않아 버전이 null이다")
  void skipsEmptySplit() {
    DatasetFixtures.upload("dev-only", DatasetFixtures.manifest("devonly", "clip-0001"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);

    DatasetImport result = imports.get(datasets.importNow("dev-only", 0.0, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
    assertThat(result.devVersion()).isEqualTo(1);
    assertThat(result.evalSetId()).isNotNull();
    assertThat(result.evalVersion()).isNull();
  }

  @Test
  @DisplayName("[SPEC-030 R-10] 같은 이름의 검토 라벨 세트가 있으면 FAILED·QUALITY_GOLDEN_ORIGIN_MISMATCH다")
  void failsOnReviewSetWithSameName() throws Exception {
    golden.create("clash-dev");
    DatasetFixtures.upload("clash", DatasetFixtures.manifest("clash", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0002", 201);

    DatasetImport result = imports.get(datasets.importNow("clash", RATIO, SEED, operator));

    assertThat(result.status()).isEqualTo(ImportStatus.FAILED);
    assertThat(result.errors())
        .extracting(e -> e.code())
        .contains("QUALITY_GOLDEN_ORIGIN_MISMATCH");
  }

  @Test
  @DisplayName("[SPEC-030 R-4] 같은 매니페스트를 다시 가져오면 같은 importId로 media를 부르고 항목을 갱신해 새 버전을 만든다")
  void reimportsSameManifest() {
    DatasetFixtures.upload("again", DatasetFixtures.manifest("againds", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0002", 201);
    UUID first = datasets.importNow("again", RATIO, SEED, operator);
    DatasetFixtures.mediaAnswers("clip-0002", 200);

    UUID second = datasets.importNow("again", RATIO, SEED, operator);

    DatasetImport result = imports.get(second);
    assertThat(second).isEqualTo(first);
    assertThat(result.devVersion()).isEqualTo(2);
    List<String> importIds = new ArrayList<>();
    for (LoggedRequest request : mediaRequests()) {
      importIds.add(request.getBodyAsString());
    }
    assertThat(importIds).hasSize(2).allMatch(body -> body.contains(first.toString()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM golden_dataset_items", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT item_id FROM golden_dataset_items", UUID.class))
        .isEqualTo(GoldenDatasetService.itemIdOf("againds", "clip-0002"));
  }

  @Test
  @DisplayName("[SPEC-030 R-7] Pod가 죽어 heartbeat가 끊긴 가져오기를 다른 작업자가 이어받아 남은 영상만 등록한다")
  void resumesStaleImport() {
    DatasetFixtures.upload("resume", DatasetFixtures.manifest("resume", "clip-0001", "clip-0002"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);
    DatasetFixtures.mediaAnswers("clip-0002", 201);
    UUID importId =
        imports.request(DatasetFixtures.manifestKey("resume"), RATIO, SEED, operator).importId();
    datasets.runWorker();
    // 첫 영상만 등록한 채 멈춘 상태로 되돌린다.
    jdbc.update(
        "UPDATE dataset_import_videos SET status = 'PENDING', source_id = NULL"
            + " WHERE external_id = 'clip-0002'");
    jdbc.update(
        "UPDATE dataset_imports SET status = 'REGISTERING', finished_at = NULL,"
            + " heartbeat_at = now() - interval '10 minutes'");
    TestcontainersConfiguration.media().resetRequests();

    datasets.runWorker();

    DatasetImport result = imports.get(importId);
    assertThat(result.status()).isEqualTo(ImportStatus.COMPLETED);
    assertThat(result.counts().registered()).isEqualTo(2);
    assertThat(mediaRequests()).hasSize(1);
    assertThat(mediaRequests().get(0).getBodyAsString()).contains("clip-0002");
  }

  @Test
  @DisplayName("[SPEC-030 R-1] 다른 작업자가 heartbeat를 갱신 중인 가져오기는 맡지 않는다")
  void doesNotTakeLiveImport() {
    imports.request(DatasetFixtures.manifestKey("live"), RATIO, SEED, operator);
    jdbc.update("UPDATE dataset_imports SET status = 'REGISTERING', heartbeat_at = now()");

    datasets.runWorker();

    assertThat(jdbc.queryForObject("SELECT status FROM dataset_imports", String.class))
        .isEqualTo("REGISTERING");
    assertThat(
            TestcontainersConfiguration.media()
                .findAll(postRequestedFor(urlEqualTo(DatasetFixtures.MEDIA_PATH))))
        .isEmpty();
  }

  private static List<LoggedRequest> mediaRequests() {
    return TestcontainersConfiguration.media()
        .findAll(
            postRequestedFor(urlEqualTo(DatasetFixtures.MEDIA_PATH))
                .withRequestBody(containing("externalId")));
  }
}
