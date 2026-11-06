package com.vlm.quality.review.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@IntegrationTest
class ReviewQueueApiTest {

  private static final UUID OPERATOR = UUID.randomUUID();

  @Autowired MockMvc mvc;
  @Autowired ReviewFixtures fixtures;
  @Autowired ObjectMapper json;

  @Test
  @DisplayName("[SPEC-029 R-5] 목록은 상태·유형으로 거르고 createdAt·itemId 순으로 쪽을 나눈다")
  void listsWithFiltersAndPages() throws Exception {
    UUID source = UUID.randomUUID();
    UUID first =
        fixtures.reviewRequired(source, "fall", List.of(ReviewFixtures.frameKey(source, 0)));
    UUID second =
        fixtures.reviewRequired(source, "ppe_missing", List.of(ReviewFixtures.frameKey(source, 1)));
    fixtures.reviewRequired(source, "fall", List.of(ReviewFixtures.frameKey(source, 2)));

    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/review-items?size=2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].itemId").value(first.toString()))
        .andExpect(jsonPath("$.page.totalElements").value(3))
        .andExpect(jsonPath("$.page.totalPages").value(2));
    mvc.perform(
            operator(
                HttpMethod.GET,
                OPERATOR,
                "/api/v1/quality/review-items?eventType=ppe_missing&status=UNASSIGNED"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].itemId").value(second.toString()));
  }

  @Test
  @DisplayName("[SPEC-029 R-27] 사유 필터로 재판정 큐만 고른다")
  void filtersByReason() throws Exception {
    UUID source = UUID.randomUUID();
    fixtures.reviewRequired(source);
    UUID budget = fixtures.unverified(source, "budget_exceeded");

    mvc.perform(
            operator(
                HttpMethod.GET, OPERATOR, "/api/v1/quality/review-items?reason=budget_exceeded"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].itemId").value(budget.toString()))
        .andExpect(jsonPath("$.items[0].reason").value("budget_exceeded"));
  }

  @Test
  @DisplayName("[SPEC-029 R-5] 모르는 상태·사유·유형으로 거르면 400 QUALITY_INVALID_REQUEST다")
  void rejectsUnknownFilters() throws Exception {
    for (String query : List.of("status=DONE", "reason=whatever", "eventType=not_a_type")) {
      mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/review-items?" + query))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
    }
  }

  @Test
  @DisplayName("[SPEC-029 R-5] 상세는 근거 프레임 경로·서명 주소·판정 결과를 담고 서명 주소로 이미지를 받는다")
  void returnsDetailWithSignedUrls() throws Exception {
    UUID source = UUID.randomUUID();
    String key = ReviewFixtures.frameKey(source, 0);
    try (S3Client s3 = TestcontainersConfiguration.s3()) {
      s3.putObject(
          p -> p.bucket(TestcontainersConfiguration.SOURCE_BUCKET).key(key),
          RequestBody.fromBytes(new byte[] {1, 2, 3}));
    }
    UUID itemId = fixtures.reviewRequired(source, "fall", List.of(key));

    String body =
        mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/review-items/{id}", itemId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.evidenceFrameKeys[0]").value(key))
            .andExpect(jsonPath("$.analysis.verificationStatus").value("confirmed"))
            .andExpect(jsonPath("$.label").isEmpty())
            .andExpect(jsonPath("$.assigneeId").isEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode item = json.readTree(body);
    URI url = URI.create(item.get("evidenceFrameUrls").get(0).asText());
    HttpResponse<byte[]> image =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(url).GET().build(), HttpResponse.BodyHandlers.ofByteArray());

    assertThat(image.statusCode()).isEqualTo(200);
    assertThat(image.body()).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("[SPEC-029 R-5] 없는 항목이면 404 QUALITY_REVIEW_NOT_FOUND다")
  void returnsNotFound() throws Exception {
    mvc.perform(
            operator(
                HttpMethod.GET, OPERATOR, "/api/v1/quality/review-items/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_REVIEW_NOT_FOUND"));
  }
}
