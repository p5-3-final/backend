package com.vlm.media.contract;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.listRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.StorageUploads;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** media-service 요청·응답이 docs 레포 계약 {@code media-service.yaml}과 일치하는지 검증한다(T025). */
// 테스트 설정: 단일 업로드 기준 1MiB, 조각 5MiB (IntegrationTest)
@IntegrationTest
class MediaContractTest {

  private static final int PART = 5 * 1024 * 1024;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  private final UUID ownerId = UUID.randomUUID();
  private ResultMatcher matchesContract;
  private ResultMatcher responseMatchesContract;

  @BeforeEach
  void loadContract() {
    // 계약 파일이 없으면 검증 없이 통과하지 않도록 바로 실패시킨다.
    Path contract =
        Path.of(
            Objects.requireNonNull(
                System.getProperty("vlm.contracts.dir"), "vlm.contracts.dir is not set"),
            "openapi",
            "media-service.yaml");
    assertThat(Files.isRegularFile(contract)).as("contract file %s", contract).isTrue();
    matchesContract = OpenApiValidationMatchers.openApi().isValid(contract.toString());
    // 일부러 계약을 어긴 요청을 보내는 실패 케이스는 응답만 검증한다.
    OpenApiInteractionValidator responseOnly =
        OpenApiInteractionValidator.createForSpecificationUrl(contract.toString())
            .withLevelResolver(
                LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build())
            .build();
    responseMatchesContract = OpenApiValidationMatchers.openApi().isValid(responseOnly);
  }

  @Test
  @DisplayName("[SPEC-003 R-2] 단일 업로드 자리 응답이 계약과 일치한다")
  void createSingle() throws Exception {
    perform(asUser(createVideoRequest("a.mp4", 1000, "video/mp4"), ownerId))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-003 R-3] 멀티파트 업로드 자리 응답이 계약과 일치한다")
  void createMultipart() throws Exception {
    perform(asUser(createVideoRequest("big.mp4", PART + 1000, "video/mp4"), ownerId))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-003 R-4] 파일 크기 오류 응답이 계약과 일치한다")
  void createFileSizeInvalid() throws Exception {
    performInvalidRequest(asUser(createVideoRequest("a.mp4", 5_368_709_121L, "video/mp4"), ownerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-003 R-5] 형식 오류 응답이 계약과 일치한다")
  void createUnsupportedFormat() throws Exception {
    performInvalidRequest(asUser(createVideoRequest("a.gif", 1000, "image/gif"), ownerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-003 R-1] 사용자 헤더가 없으면 401 응답이 계약과 일치한다")
  void createUnauthenticated() throws Exception {
    performInvalidRequest(createVideoRequest("a.mp4", 1000, "video/mp4"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[SPEC-003 R-7] 단일 업로드 완료 응답이 계약과 일치한다")
  void completeSingle() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);

    perform(asUser(completeRequest(id(created)), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-003 R-8] 멀티파트 완료 응답이 계약과 일치한다")
  void completeMultipart() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "big.mp4", PART + 1000, "video/mp4");
    List<Map<String, Object>> parts =
        StorageUploads.putParts(created.get("upload"), new byte[PART + 1000]);

    perform(asUser(completeRequest(id(created), parts), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-003 R-10] 업로드 미완료 응답이 계약과 일치한다")
  void completeIncomplete() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    perform(asUser(completeRequest(id(created)), ownerId)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("[SPEC-003 R-12] 상태 오류 응답이 계약과 일치한다")
  void completeInvalidState() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    jdbc.update("UPDATE videos SET status = 'EXPIRED' WHERE public_id = ?::uuid", id(created));

    perform(asUser(completeRequest(id(created)), ownerId)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 목록 응답이 계약과 일치한다")
  void list() throws Exception {
    createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(
        createVideo(mvc, ownerId, "b.mp4", 1000, "video/mp4").get("upload"), new byte[1000]);

    perform(asUser(listRequest().param("page", "0").param("size", "10"), ownerId))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-003 R-14] 목록 요청 형식 오류 응답이 계약과 일치한다")
  void listBadRequest() throws Exception {
    performInvalidRequest(asUser(listRequest().param("size", "101"), ownerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-003 R-15] 상세 응답이 계약과 일치한다")
  void get() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    perform(asUser(getRequest(id(created)), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 영상 없음 응답이 계약과 일치한다")
  void getNotFound() throws Exception {
    perform(asUser(getRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("[SPEC-003 R-18] 삭제 응답이 계약과 일치한다")
  void delete() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    perform(asUser(deleteRequest(id(created)), ownerId)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("[SPEC-003 R-16] 삭제할 영상 없음 응답이 계약과 일치한다")
  void deleteNotFound() throws Exception {
    perform(asUser(deleteRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound());
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andExpect(matchesContract);
  }

  private ResultActions performInvalidRequest(MockHttpServletRequestBuilder request)
      throws Exception {
    return mvc.perform(request).andExpect(responseMatchesContract);
  }

  private static String id(JsonNode created) {
    return created.get("video").get("id").asText();
  }
}
