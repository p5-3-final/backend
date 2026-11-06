package com.vlm.quality.golden.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GoldenSetApiTest {

  private static final UUID OPERATOR = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired GoldenFixtures golden;
  @Autowired ReviewFixtures reviews;

  @Test
  @DisplayName("[SPEC-029 R-11] Golden Set을 만들면 201이고 origin은 review, 초안·버전·기준은 비어 있다")
  void creates() throws Exception {
    mvc.perform(
            operator(HttpMethod.POST, OPERATOR, "/api/v1/quality/golden-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ppe-eval\",\"split\":\"eval\",\"description\":\"보호구\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("ppe-eval"))
        .andExpect(jsonPath("$.split").value("eval"))
        .andExpect(jsonPath("$.origin").value("review"))
        .andExpect(jsonPath("$.draftEntryCount").value(0))
        .andExpect(jsonPath("$.latestVersion").isEmpty())
        .andExpect(jsonPath("$.baselineRunId").isEmpty());
  }

  @Test
  @DisplayName("[SPEC-029 R-11] 같은 이름이면 409 QUALITY_GOLDEN_SET_DUPLICATED다")
  void rejectsDuplicateName() throws Exception {
    golden.create("dup-set");

    mvc.perform(
            operator(HttpMethod.POST, OPERATOR, "/api/v1/quality/golden-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"dup-set\",\"split\":\"dev\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_DUPLICATED"));
  }

  @Test
  @DisplayName("[SPEC-029 R-11] 이름 형식이 틀리면 400 QUALITY_INVALID_REQUEST다")
  void rejectsInvalidName() throws Exception {
    mvc.perform(
            operator(HttpMethod.POST, OPERATOR, "/api/v1/quality/golden-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad Name\",\"split\":\"dev\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
  }

  @Test
  @DisplayName("[SPEC-029 R-11] 목록은 이름 순이고 상세는 초안 수를 담는다, 없으면 404다")
  void listsAndGets() throws Exception {
    UUID b = golden.create("b-set");
    golden.create("a-set");
    golden.addEntry(b, reviews.labeled(UUID.randomUUID()));

    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/golden-sets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("a-set"))
        .andExpect(jsonPath("$.items[1].name").value("b-set"))
        .andExpect(jsonPath("$.page.totalElements").value(2));
    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/golden-sets/{id}", b))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.draftEntryCount").value(1));
    mvc.perform(
            operator(
                HttpMethod.GET, OPERATOR, "/api/v1/quality/golden-sets/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_NOT_FOUND"));
  }
}
