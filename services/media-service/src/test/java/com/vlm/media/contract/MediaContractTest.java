package com.vlm.media.contract;

import static com.vlm.media.support.MediaApi.asUser;
import static com.vlm.media.support.MediaApi.completeRequest;
import static com.vlm.media.support.MediaApi.consentRequest;
import static com.vlm.media.support.MediaApi.createSessionRequest;
import static com.vlm.media.support.MediaApi.createSessionVideo;
import static com.vlm.media.support.MediaApi.createVideo;
import static com.vlm.media.support.MediaApi.createVideoRequest;
import static com.vlm.media.support.MediaApi.deleteRequest;
import static com.vlm.media.support.MediaApi.getRequest;
import static com.vlm.media.support.MediaApi.listRequest;
import static com.vlm.media.support.MediaApi.playbackRequest;
import static com.vlm.media.support.MediaApi.retainedVideosRequest;
import static com.vlm.media.support.MediaApi.retentionSettingsRequest;
import static com.vlm.media.support.MediaApi.revokeAllRequest;
import static com.vlm.media.support.MediaApi.revokeRequest;
import static com.vlm.media.support.MediaApi.sessionRequest;
import static com.vlm.media.support.MediaApi.sessionsRequest;
import static com.vlm.media.support.MediaApi.updateRetentionSettingsRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.media.normalization.application.NormalizationEventService;
import com.vlm.media.shared.domain.RejectReason;
import com.vlm.media.support.IntegrationTest;
import com.vlm.media.support.MediaApi;
import com.vlm.media.support.Normalizations;
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
  @Autowired NormalizationEventService normalization;

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
  @DisplayName("[SPEC-007 R-21] 준비됨 영상의 상세 응답이 계약과 일치한다")
  void getReady() throws Exception {
    String id = id(createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4"));
    Normalizations.ready(normalization, jdbc, id);

    perform(asUser(getRequest(id), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-007 R-21] 거절된 영상의 상세 응답이 계약과 일치한다")
  void getRejected() throws Exception {
    String id = id(createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4"));
    Normalizations.rejected(normalization, jdbc, id, RejectReason.DECODE_FAILED);

    perform(asUser(getRequest(id), ownerId)).andExpect(status().isOk());
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

  @Test
  @DisplayName("[SPEC-006 R-1] 재생 주소 응답이 계약과 일치한다")
  void playback() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");
    StorageUploads.postForm(created.get("upload"), new byte[1000]);
    mvc.perform(asUser(completeRequest(id(created)), ownerId)).andExpect(status().isOk());

    perform(asUser(playbackRequest(id(created)), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-006 R-4] 재생할 수 없는 영상 응답이 계약과 일치한다")
  void playbackNotPlayable() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    perform(asUser(playbackRequest(id(created)), ownerId)).andExpect(status().isConflict());
  }

  @Test
  @DisplayName("[SPEC-006 R-5] 재생할 영상 없음 응답이 계약과 일치한다")
  void playbackNotFound() throws Exception {
    perform(asUser(playbackRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("[SPEC-017 R-1] 세션 만들기 응답이 계약과 일치한다")
  void createSession() throws Exception {
    perform(asUser(createSessionRequest(Map.of("name", "세션", "site", "공장")), ownerId))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-017 R-2] 세션 만들기 입력 오류 응답이 계약과 일치한다")
  void createSessionBadRequest() throws Exception {
    performInvalidRequest(asUser(createSessionRequest(Map.of("name", "")), ownerId))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-017 R-3] 세션 목록 응답이 계약과 일치한다")
  void listSessions() throws Exception {
    MediaApi.createSession(mvc, ownerId, "세션");

    perform(asUser(sessionsRequest(), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-017 R-4] 세션 상세 응답이 계약과 일치한다")
  void getSession() throws Exception {
    String sessionId = MediaApi.createSession(mvc, ownerId, "세션");
    createSessionVideo(mvc, ownerId, sessionId, "BEFORE");
    createSessionVideo(mvc, ownerId, sessionId, "AFTER");

    perform(asUser(sessionRequest(sessionId), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-017 R-5] 세션 없음 응답이 계약과 일치한다")
  void getSessionNotFound() throws Exception {
    perform(asUser(sessionRequest(UUID.randomUUID().toString()), ownerId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("[SPEC-017 R-6] 세션에 넣은 업로드 자리 응답이 계약과 일치한다")
  void createSessionVideoMatches() throws Exception {
    String sessionId = MediaApi.createSession(mvc, ownerId, "세션");

    perform(
            asUser(
                createVideoRequest(
                    "a.mp4", 1000, "video/mp4", Map.of("sessionId", sessionId, "role", "DURING")),
                ownerId))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-017 R-9] 업로드 자리 발급의 세션 없음 응답이 계약과 일치한다")
  void createVideoSessionNotFound() throws Exception {
    perform(
            asUser(
                createVideoRequest(
                    "a.mp4",
                    1000,
                    "video/mp4",
                    Map.of("sessionId", UUID.randomUUID().toString(), "role", "DURING")),
                ownerId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("[SPEC-017 R-10] 업로드 자리 발급의 세션 가득 참 응답이 계약과 일치한다")
  void createVideoSessionFull() throws Exception {
    String sessionId = MediaApi.createSession(mvc, ownerId, "세션");
    jdbc.update(
        """
        INSERT INTO videos (public_id, owner_id, file_name, size_bytes, content_type, status,
            storage_key, expires_at, session_id, session_role)
        SELECT gen_random_uuid(), ?, 'v.mp4', 1, 'video/mp4', 'UPLOADED', 'k', now(), ?, 'DURING'
        FROM generate_series(1, 100)
        """,
        ownerId,
        UUID.fromString(sessionId));

    perform(
            asUser(
                createVideoRequest(
                    "a.mp4", 1000, "video/mp4", Map.of("sessionId", sessionId, "role", "DURING")),
                ownerId))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("[SPEC-018 R-1] [SPEC-018 R-2] 보존 설정 조회·변경 응답이 계약과 일치한다")
  void retentionSettings() throws Exception {
    perform(asUser(retentionSettingsRequest(), ownerId)).andExpect(status().isOk());
    perform(asUser(updateRetentionSettingsRequest(true), ownerId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-018 R-4] 동의 값을 넣은 업로드 자리 응답이 계약과 일치한다")
  void createVideoWithConsent() throws Exception {
    perform(
            asUser(
                createVideoRequest("a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                ownerId))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("[SPEC-018 R-6] [SPEC-018 R-7] 영상 동의 변경 응답과 없음 응답이 계약과 일치한다")
  void changeConsent() throws Exception {
    JsonNode created = createVideo(mvc, ownerId, "a.mp4", 1000, "video/mp4");

    perform(asUser(consentRequest(id(created), true), ownerId)).andExpect(status().isOk());
    perform(asUser(consentRequest(UUID.randomUUID().toString(), true), ownerId))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "[SPEC-018 R-13] [SPEC-018 R-14] [SPEC-018 R-15] [SPEC-018 R-16] 보존 목록·철회·전체 철회 응답이 계약과 일치한다")
  void retainedVideos() throws Exception {
    String videoId =
        MediaApi.json(
                mvc.perform(
                    asUser(
                        createVideoRequest(
                            "a.mp4", 1000, "video/mp4", Map.of("retentionConsent", true)),
                        ownerId)))
            .at("/video/id")
            .asText();
    mvc.perform(asUser(deleteRequest(videoId), ownerId)).andExpect(status().isNoContent());

    perform(asUser(retainedVideosRequest(), ownerId)).andExpect(status().isOk());
    perform(asUser(revokeRequest(videoId), ownerId)).andExpect(status().isNoContent());
    perform(asUser(revokeRequest(videoId), ownerId)).andExpect(status().isNotFound());
    perform(asUser(revokeAllRequest(), ownerId)).andExpect(status().isNoContent());
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
