package com.vlm.quality.dataset.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class DatasetImportApiTest {

  private static final String IMPORTS = "/api/v1/quality/dataset-imports";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID me = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-030 R-1] 가져오기를 요청하면 importId=uuid5(manifestKey)인 PENDING 가져오기를 202로 돌려준다")
  void acceptsImport() throws Exception {
    String key = "imports/accept/manifest.json";

    request("{\"manifestKey\":\"" + key + "\"}")
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.importId").value(DatasetImportService.importIdOf(key).toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.datasetName").value(nullValue()))
        .andExpect(jsonPath("$.evalRatio").value(0.2))
        .andExpect(jsonPath("$.splitSeed").value(0))
        .andExpect(jsonPath("$.counts.videos").value(0))
        .andExpect(jsonPath("$.goldenSets").value(nullValue()))
        .andExpect(jsonPath("$.requestedBy").value(me.toString()));
  }

  @Test
  @DisplayName("[SPEC-030 R-1] 같은 매니페스트의 가져오기가 진행 중이면 409 QUALITY_IMPORT_IN_PROGRESS다")
  void rejectsWhileInProgress() throws Exception {
    String body = "{\"manifestKey\":\"imports/busy/manifest.json\"}";
    request(body).andExpect(status().isAccepted());

    for (String state : new String[] {"PENDING", "VALIDATING", "REGISTERING"}) {
      jdbc.update("UPDATE dataset_imports SET status = ?", state);
      request(body)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("QUALITY_IMPORT_IN_PROGRESS"));
    }
  }

  @Test
  @DisplayName("[SPEC-030 R-1] 끝난 매니페스트를 다시 요청하면 개수·오류를 비우고 새 비율로 다시 PENDING이 된다")
  void restartsFinishedImport() throws Exception {
    String key = "imports/again/manifest.json";
    request("{\"manifestKey\":\"" + key + "\"}").andExpect(status().isAccepted());
    jdbc.update(
        "UPDATE dataset_imports SET status = 'COMPLETED', video_count = 3, registered_count = 2,"
            + " failed_count = 1, errors = '[{\"externalId\":\"x\",\"code\":\"C\",\"message\":\"m\"}]',"
            + " dev_version = 1, finished_at = now()");

    request("{\"manifestKey\":\"" + key + "\",\"evalRatio\":0.5,\"splitSeed\":9}")
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.importId").value(DatasetImportService.importIdOf(key).toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.evalRatio").value(0.5))
        .andExpect(jsonPath("$.splitSeed").value(9))
        .andExpect(jsonPath("$.counts.registered").value(0))
        .andExpect(jsonPath("$.errors").isEmpty())
        .andExpect(jsonPath("$.finishedAt").value(nullValue()));
  }

  @Test
  @DisplayName("[SPEC-030 R-1] 매니페스트 경로 형식이 계약과 다르면 400 QUALITY_INVALID_REQUEST다")
  void rejectsInvalidKey() throws Exception {
    request("{\"manifestKey\":\"imports/a/b/manifest.json\"}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
    request("{\"manifestKey\":\"imports/a/manifest.json\",\"evalRatio\":1.5}")
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-9] 상태 조회는 개수·Golden Set 버전·오류를 담고, 없는 가져오기는 404 QUALITY_IMPORT_NOT_FOUND다")
  void returnsImportState() throws Exception {
    String key = "imports/state/manifest.json";
    request("{\"manifestKey\":\"" + key + "\"}").andExpect(status().isAccepted());
    UUID dev = UUID.randomUUID();
    UUID eval = UUID.randomUUID();
    jdbc.update(
        "UPDATE dataset_imports SET status = 'COMPLETED', dataset_name = 'ds', video_count = 3,"
            + " registered_count = 2, failed_count = 1, label_count = 7, dev_count = 1,"
            + " eval_count = 1, dev_set_id = ?, eval_set_id = ?, dev_version = 2,"
            + " eval_version = NULL, finished_at = now(), errors ="
            + " '[{\"externalId\":\"clip-3\",\"code\":\"QUALITY_IMPORT_VIDEO_NOT_FOUND\","
            + "\"message\":\"없음\"}]'",
        dev,
        eval);

    mvc.perform(
            operator(HttpMethod.GET, me, IMPORTS + "/{id}", DatasetImportService.importIdOf(key)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.datasetName").value("ds"))
        .andExpect(jsonPath("$.counts.videos").value(3))
        .andExpect(jsonPath("$.counts.registered").value(2))
        .andExpect(jsonPath("$.counts.failed").value(1))
        .andExpect(jsonPath("$.counts.labels").value(7))
        .andExpect(jsonPath("$.counts.dev").value(1))
        .andExpect(jsonPath("$.counts.eval").value(1))
        .andExpect(jsonPath("$.goldenSets.devSetId").value(dev.toString()))
        .andExpect(jsonPath("$.goldenSets.devVersion").value(2))
        .andExpect(jsonPath("$.goldenSets.evalVersion").value(nullValue()))
        .andExpect(jsonPath("$.errors[0].externalId").value("clip-3"))
        .andExpect(jsonPath("$.finishedAt").isNotEmpty());
    mvc.perform(operator(HttpMethod.GET, me, IMPORTS + "/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_IMPORT_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-030 R-9] 목록은 접수 시각 내림차순으로 쪽을 나눈다")
  void listsNewestFirst() throws Exception {
    request("{\"manifestKey\":\"imports/first/manifest.json\"}").andExpect(status().isAccepted());
    request("{\"manifestKey\":\"imports/second/manifest.json\"}").andExpect(status().isAccepted());

    mvc.perform(operator(HttpMethod.GET, me, IMPORTS + "?page=0&size=1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].manifestKey").value("imports/second/manifest.json"))
        .andExpect(jsonPath("$.page.totalElements").value(2))
        .andExpect(jsonPath("$.page.totalPages").value(2));
  }

  private ResultActions request(String body) throws Exception {
    return mvc.perform(
        operator(HttpMethod.POST, me, IMPORTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}
