package com.vlm.quality.review.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class ReviewLabelApiTest {

  private static final String LABEL_PATH = "/api/v1/quality/review-items/{id}/label";

  @Autowired MockMvc mvc;
  @Autowired ReviewFixtures fixtures;

  @Test
  @DisplayName("[SPEC-029 R-8] 맡은 사람이 라벨을 저장하면 200·LABELED이고 다시 저장하면 덮어쓴다")
  void savesAndOverwritesLabel() throws Exception {
    UUID me = UUID.randomUUID();
    UUID itemId = assigned(me);

    label(me, itemId, ReviewAssignmentApiTest.LABEL)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("LABELED"))
        .andExpect(jsonPath("$.label.verdict").value("correct"))
        .andExpect(jsonPath("$.labeledAt").isNotEmpty());
    label(me, itemId, ReviewAssignmentApiTest.LABEL.replace("\"correct\"", "\"incorrect\""))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.label.verdict").value("incorrect"));
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-8] 다른 사람은 403 QUALITY_REVIEW_NOT_ASSIGNEE, UNASSIGNED 항목은 409 QUALITY_REVIEW_NOT_ASSIGNED다")
  void rejectsOthersAndUnassigned() throws Exception {
    UUID itemId = assigned(UUID.randomUUID());
    UUID free = fixtures.reviewRequired(UUID.randomUUID());

    label(UUID.randomUUID(), itemId, ReviewAssignmentApiTest.LABEL)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_ASSIGNEE"));
    label(UUID.randomUUID(), free, ReviewAssignmentApiTest.LABEL)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_ASSIGNED"));
  }

  @Test
  @DisplayName("[SPEC-029 R-9] 규칙을 어긴 라벨은 400 QUALITY_INVALID_REQUEST이고 항목은 그대로다")
  void rejectsInvalidLabel() throws Exception {
    UUID me = UUID.randomUUID();
    UUID itemId = assigned(me);

    label(me, itemId, ReviewAssignmentApiTest.LABEL.replace("\"endMs\":4000", "\"endMs\":1000"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
    label(
            me,
            itemId,
            ReviewAssignmentApiTest.LABEL.replace(
                "\"eventPresent\":true", "\"eventPresent\":false"))
        .andExpect(status().isBadRequest());
    mvc.perform(operator(HttpMethod.GET, me, "/api/v1/quality/review-items/{id}", itemId))
        .andExpect(jsonPath("$.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.label").isEmpty());
  }

  private UUID assigned(UUID operator) throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    mvc.perform(
            operator(
                HttpMethod.POST, operator, "/api/v1/quality/review-items/{id}/assignment", itemId))
        .andExpect(status().isOk());
    return itemId;
  }

  private ResultActions label(UUID operator, UUID itemId, String body) throws Exception {
    return mvc.perform(
        operator(HttpMethod.PUT, operator, LABEL_PATH, itemId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}
