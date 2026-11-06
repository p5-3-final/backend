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

@IntegrationTest
class ReviewAssignmentApiTest {

  static final String LABEL =
      "{\"verdict\":\"correct\",\"eventPresent\":true,\"eventType\":\"fall\",\"startMs\":1000,"
          + "\"endMs\":4000,\"severity\":\"high\",\"note\":null}";
  private static final String ASSIGNMENT = "/api/v1/quality/review-items/{id}/assignment";

  @Autowired MockMvc mvc;
  @Autowired ReviewFixtures fixtures;

  @Test
  @DisplayName("[SPEC-029 R-6] UNASSIGNED 항목을 맡으면 200이고 ASSIGNED·맡은 사람이 요청자다")
  void assigns() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    UUID me = UUID.randomUUID();

    mvc.perform(operator(HttpMethod.POST, me, ASSIGNMENT, itemId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ASSIGNED"))
        .andExpect(jsonPath("$.assigneeId").value(me.toString()))
        .andExpect(jsonPath("$.assignedAt").isNotEmpty());
  }

  @Test
  @DisplayName("[SPEC-029 R-6] 이미 다른 사람이 맡았으면 409 QUALITY_REVIEW_ALREADY_ASSIGNED다")
  void rejectsAlreadyAssigned() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    mvc.perform(operator(HttpMethod.POST, UUID.randomUUID(), ASSIGNMENT, itemId))
        .andExpect(status().isOk());

    mvc.perform(operator(HttpMethod.POST, UUID.randomUUID(), ASSIGNMENT, itemId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_ALREADY_ASSIGNED"));
  }

  @Test
  @DisplayName("[SPEC-029 R-6] 라벨이 끝난 항목은 409 QUALITY_REVIEW_ALREADY_LABELED, 없는 항목은 404다")
  void rejectsLabeledAndMissing() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    UUID me = UUID.randomUUID();
    mvc.perform(operator(HttpMethod.POST, me, ASSIGNMENT, itemId)).andExpect(status().isOk());
    mvc.perform(
            operator(HttpMethod.PUT, me, "/api/v1/quality/review-items/{id}/label", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(LABEL))
        .andExpect(status().isOk());

    mvc.perform(operator(HttpMethod.POST, UUID.randomUUID(), ASSIGNMENT, itemId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_ALREADY_LABELED"));
    mvc.perform(operator(HttpMethod.POST, me, ASSIGNMENT, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-029 R-7] 맡은 사람이 내려놓으면 204이고 UNASSIGNED로 돌아간다")
  void releases() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    UUID me = UUID.randomUUID();
    mvc.perform(operator(HttpMethod.POST, me, ASSIGNMENT, itemId)).andExpect(status().isOk());

    mvc.perform(operator(HttpMethod.DELETE, me, ASSIGNMENT, itemId))
        .andExpect(status().isNoContent());

    mvc.perform(operator(HttpMethod.GET, me, "/api/v1/quality/review-items/{id}", itemId))
        .andExpect(jsonPath("$.status").value("UNASSIGNED"))
        .andExpect(jsonPath("$.assigneeId").isEmpty());
  }

  @Test
  @DisplayName(
      "[SPEC-029 R-7] 다른 사람이 내려놓으면 403 QUALITY_REVIEW_NOT_ASSIGNEE, 맡지 않은 항목은 409 QUALITY_REVIEW_NOT_ASSIGNED다")
  void rejectsReleaseByOthers() throws Exception {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    UUID free = fixtures.reviewRequired(UUID.randomUUID());
    mvc.perform(operator(HttpMethod.POST, UUID.randomUUID(), ASSIGNMENT, itemId))
        .andExpect(status().isOk());

    mvc.perform(operator(HttpMethod.DELETE, UUID.randomUUID(), ASSIGNMENT, itemId))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_ASSIGNEE"));
    mvc.perform(operator(HttpMethod.DELETE, UUID.randomUUID(), ASSIGNMENT, free))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_ASSIGNED"));
  }
}
