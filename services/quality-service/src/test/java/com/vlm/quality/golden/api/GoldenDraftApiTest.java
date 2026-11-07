package com.vlm.quality.golden.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.review.application.ReviewLabelService;
import com.vlm.quality.shared.domain.ReviewLabel;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GoldenDraftApiTest {

  private static final UUID OPERATOR = UUID.randomUUID();
  private static final String ENTRIES = "/api/v1/quality/golden-sets/{setId}/entries";

  @Autowired MockMvc mvc;
  @Autowired GoldenFixtures golden;
  @Autowired ReviewFixtures reviews;
  @Autowired ReviewLabelService labels;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-029 R-12] 라벨이 붙은 항목을 넣으면 201이고 그때 라벨을 복사한다, 나중에 라벨을 고쳐도 초안은 그대로다")
  void addsWithLabelCopy() throws Exception {
    UUID setId = golden.create("draft-set");
    UUID source = UUID.randomUUID();
    UUID itemId = reviews.labeled(source);

    JsonNode entry = golden.addEntry(setId, itemId);
    UUID assignee =
        jdbc.queryForObject(
            "SELECT assignee_id FROM review_items WHERE item_id = ?", UUID.class, itemId);
    labels.label(
        itemId, assignee, new ReviewLabel("incorrect", false, null, null, null, null, "아님"));

    mvc.perform(operator(HttpMethod.GET, OPERATOR, ENTRIES, setId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].entryId").value(entry.get("entryId").asText()))
        .andExpect(jsonPath("$.items[0].reviewItemId").value(itemId.toString()))
        .andExpect(jsonPath("$.items[0].sourceId").value(source.toString()))
        .andExpect(jsonPath("$.items[0].label.verdict").value("correct"));
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-12] 라벨이 없거나 uncertain이거나 판정 결과가 없으면 409 QUALITY_REVIEW_NOT_LABELED, 이미 든 항목은 409 QUALITY_GOLDEN_ENTRY_DUPLICATED다")
  void rejectsUnlabeledUncertainAndDuplicate() throws Exception {
    UUID setId = golden.create("reject-set");
    UUID unlabeled = reviews.reviewRequired(UUID.randomUUID());
    UUID source = UUID.randomUUID();
    UUID uncertain =
        reviews.labeled(
            source,
            List.of(ReviewFixtures.frameKey(source, 0)),
            new ReviewLabel("uncertain", false, null, null, null, null, null));
    UUID labeled = reviews.labeled(UUID.randomUUID());
    golden.addEntry(setId, labeled);
    // 판정 결과(analysis)가 없는 항목은 매니페스트 entries.analysis를 채울 수 없다.
    UUID withoutAnalysis = reviews.labeledDismissedAlert(UUID.randomUUID());

    for (UUID itemId : List.of(unlabeled, uncertain, withoutAnalysis)) {
      add(setId, itemId)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_LABELED"));
    }
    add(setId, labeled)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_ENTRY_DUPLICATED"));
    add(setId, UUID.randomUUID())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-029 R-13] 초안 항목을 빼면 204이고, 없는 항목은 404 QUALITY_GOLDEN_ENTRY_NOT_FOUND다")
  void removesEntry() throws Exception {
    UUID setId = golden.create("remove-set");
    JsonNode entry = golden.addEntry(setId, reviews.labeled(UUID.randomUUID()));

    mvc.perform(
            operator(
                HttpMethod.DELETE,
                OPERATOR,
                ENTRIES + "/{entryId}",
                setId,
                entry.get("entryId").asText()))
        .andExpect(status().isNoContent());
    mvc.perform(operator(HttpMethod.GET, OPERATOR, ENTRIES, setId))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(
            operator(HttpMethod.DELETE, OPERATOR, ENTRIES + "/{entryId}", setId, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_ENTRY_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-029 R-13] 확정한 뒤 초안에서 빼도 확정 버전의 항목 수와 매니페스트는 그대로다")
  void removalDoesNotChangeFrozenVersion() throws Exception {
    UUID setId = golden.create("frozen-set");
    UUID source = UUID.randomUUID();
    String key = StorageFixtures.putSourceFrame(source, 0);
    JsonNode entry =
        golden.addEntry(setId, reviews.labeled(source, List.of(key), ReviewFixtures.CORRECT));
    JsonNode version = golden.freeze(setId);
    byte[] manifest = StorageFixtures.goldenObject(version.get("manifestKey").asText());

    mvc.perform(
            operator(
                HttpMethod.DELETE,
                OPERATOR,
                ENTRIES + "/{entryId}",
                setId,
                entry.get("entryId").asText()))
        .andExpect(status().isNoContent());

    mvc.perform(
            operator(
                HttpMethod.GET, OPERATOR, "/api/v1/quality/golden-sets/{setId}/versions", setId))
        .andExpect(jsonPath("$.items[0].entryCount").value(1));
    org.assertj.core.api.Assertions.assertThat(
            StorageFixtures.goldenObject(version.get("manifestKey").asText()))
        .isEqualTo(manifest);
  }

  private org.springframework.test.web.servlet.ResultActions add(UUID setId, UUID itemId)
      throws Exception {
    return mvc.perform(
        operator(HttpMethod.POST, OPERATOR, ENTRIES, setId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reviewItemId\":\"" + itemId + "\"}"));
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-10] 데이터셋 Golden Set(origin import)에 검토 항목을 넣으면 409 QUALITY_GOLDEN_ORIGIN_MISMATCH다")
  void rejectsReviewItemInDatasetSet() throws Exception {
    UUID setId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO golden_sets (set_id, name, split, origin) VALUES (?, 'ds-dev', 'dev', 'import')",
        setId);

    add(setId, reviews.labeled(UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_ORIGIN_MISMATCH"));
  }
}
