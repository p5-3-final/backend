package com.vlm.quality.contract;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.vlm.quality.TestcontainersConfiguration;
import com.vlm.quality.dataset.application.DatasetImportService;
import com.vlm.quality.review.domain.ReviewReason;
import com.vlm.quality.stage.application.DailyStageAggregationJob;
import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.EvaluationFixtures;
import com.vlm.quality.support.ExportFixtures;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StageFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * quality-service 요청·응답이 계약 {@code quality-service.yaml} 3.0.0과 일치하는지 검증한다(SPEC-029 T034·T040,
 * SPEC-030 T026).
 */
@IntegrationTest
class QualityContractTest {

  private static final String ITEMS = "/api/v1/quality/review-items";
  private static final String SETS = "/api/v1/quality/golden-sets";
  private static final String IMPORTS = "/api/v1/quality/dataset-imports";
  private static final String EXPORTS = "/api/v1/quality/feature-exports";
  private static final String LABEL =
      "{\"verdict\":\"correct\",\"eventPresent\":true,\"eventType\":\"fall\",\"startMs\":1000,"
          + "\"endMs\":4000,\"severity\":\"high\",\"note\":null}";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ReviewFixtures reviews;
  @Autowired GoldenFixtures golden;
  @Autowired EvaluationFixtures evaluations;
  @Autowired StageTimingIngestService ingest;
  @Autowired DailyStageAggregationJob aggregation;
  @Autowired DatasetFixtures datasets;
  @Autowired ExportFixtures exportFixtures;

  private final UUID me = UUID.randomUUID();
  private Path contract;
  private ResultMatcher matchesContract;
  private ResultMatcher responseMatchesContract;

  @BeforeEach
  void loadContract() {
    // 계약 파일이 없으면 검증 없이 통과하지 않도록 바로 실패시킨다.
    contract =
        Path.of(
            Objects.requireNonNull(
                System.getProperty("vlm.contracts.dir"), "vlm.contracts.dir is not set"),
            "openapi",
            "quality-service.yaml");
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
  @DisplayName("[SPEC-029 R-27] 서버가 아는 검토 사유 목록이 계약 x-extensible-enum과 같다")
  void reviewReasonsMatchContract() throws Exception {
    JsonNode spec = new ObjectMapper(new YAMLFactory()).readTree(contract.toFile());
    List<String> contractValues = new ArrayList<>();
    spec.at("/components/schemas/ReviewItem/properties/reason/x-extensible-enum")
        .forEach(v -> contractValues.add(v.asText()));

    assertThat(contractValues)
        .isNotEmpty()
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(ReviewReason.values()).map(ReviewReason::wire).toList());
  }

  @Test
  @DisplayName("[SPEC-029 R-4·R-5] 검토 목록·상세 응답이 계약과 일치한다")
  void reviewQueue() throws Exception {
    UUID source = UUID.randomUUID();
    UUID itemId =
        reviews.reviewRequired(source, "fall", List.of(StorageFixtures.putSourceFrame(source, 0)));
    reviews.unverified(UUID.randomUUID(), "budget_exceeded");

    perform(operator(HttpMethod.GET, me, ITEMS + "?status=UNASSIGNED&page=0&size=10"))
        .andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, ITEMS + "?reason=budget_exceeded"))
        .andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, ITEMS + "/{id}", itemId)).andExpect(status().isOk());
    // 계약 3.0.0: 판정 결과가 없는 항목은 analysis null, 영상 시각은 int64
    perform(operator(HttpMethod.GET, me, ITEMS + "/{id}", reviews.dismissedAlert(source)))
        .andExpect(status().isOk());
    long beyondInt32 = Integer.MAX_VALUE + 1_000L;
    perform(
            operator(
                HttpMethod.GET,
                me,
                ITEMS + "/{id}",
                reviews.reviewRequired(source, beyondInt32, beyondInt32 + 1_000)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-029 R-4·R-5] 검토 API 오류 응답(403·404·400)이 계약과 일치한다")
  void reviewErrors() throws Exception {
    perform(
            MockMvcRequestBuilders.get(ITEMS)
                .header("X-User-Id", me.toString())
                .header("X-User-Roles", "USER"))
        .andExpect(status().isForbidden());
    perform(operator(HttpMethod.GET, me, ITEMS + "/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    // 목록 필터 reason은 계약에서 닫힌 enum이라 모르는 값은 요청부터 계약 위반이다.
    performInvalidRequest(operator(HttpMethod.GET, me, ITEMS + "?reason=unknown_reason"))
        .andExpect(status().isBadRequest());
    performInvalidRequest(MockMvcRequestBuilders.get(ITEMS)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("[SPEC-029 R-6~R-9] 맡기·풀기·라벨 저장과 충돌 응답이 계약과 일치한다")
  void assignmentAndLabel() throws Exception {
    UUID itemId = reviews.reviewRequired(UUID.randomUUID());
    String assignment = ITEMS + "/{id}/assignment";

    perform(operator(HttpMethod.POST, me, assignment, itemId)).andExpect(status().isOk());
    perform(operator(HttpMethod.POST, UUID.randomUUID(), assignment, itemId))
        .andExpect(status().isConflict());
    perform(
            operator(HttpMethod.PUT, me, ITEMS + "/{id}/label", itemId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(LABEL))
        .andExpect(status().isOk());

    UUID other = reviews.reviewRequired(UUID.randomUUID());
    perform(operator(HttpMethod.POST, me, assignment, other)).andExpect(status().isOk());
    perform(operator(HttpMethod.DELETE, me, assignment, other)).andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("[SPEC-029 R-10~R-15] Golden Set 생성·조회·초안·확정·버전 목록 응답이 계약과 일치한다")
  void goldenSets() throws Exception {
    perform(
            operator(HttpMethod.POST, me, SETS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"contract-set\",\"split\":\"eval\",\"description\":null}"))
        .andExpect(status().isCreated());
    UUID setId = golden.create("contract-entries");
    UUID source = UUID.randomUUID();
    UUID labeled =
        reviews.labeled(
            source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT);

    perform(
            operator(HttpMethod.POST, me, SETS + "/{setId}/entries", setId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reviewItemId\":\"" + labeled + "\"}"))
        .andExpect(status().isCreated());
    UUID removable = reviews.labeled(UUID.randomUUID());
    String entryId = golden.addEntry(setId, removable).get("entryId").asText();
    perform(operator(HttpMethod.GET, me, SETS + "/{setId}/entries", setId))
        .andExpect(status().isOk());
    perform(operator(HttpMethod.DELETE, me, SETS + "/{setId}/entries/{entryId}", setId, entryId))
        .andExpect(status().isNoContent());
    perform(operator(HttpMethod.POST, me, SETS + "/{setId}/versions", setId))
        .andExpect(status().isCreated());
    perform(operator(HttpMethod.GET, me, SETS + "/{setId}/versions", setId))
        .andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, SETS + "/{setId}", setId)).andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, SETS + "?page=0&size=10")).andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, SETS + "/{setId}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("[SPEC-029 R-17~R-20] 평가 실행 제출·기준 지정·목록·상세 응답이 계약과 일치한다")
  void evaluationRuns() throws Exception {
    UUID setId = evaluations.frozenSet("contract-eval");
    UUID baseline = UUID.randomUUID();
    perform(internal(EvaluationFixtures.run(baseline, setId, 1, "0.90"), "quality-evals"))
        .andExpect(status().isCreated());
    perform(
            operator(HttpMethod.PUT, me, SETS + "/{setId}/baseline", setId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"runId\":\"" + baseline + "\"}"))
        .andExpect(status().isOk());
    UUID candidate = UUID.randomUUID();
    String body = EvaluationFixtures.run(candidate, setId, 1, "0.80");
    perform(internal(body, "quality-evals")).andExpect(status().isCreated());
    perform(internal(body, "quality-evals")).andExpect(status().isOk());
    perform(internal(body, "someone-else")).andExpect(status().isForbidden());

    perform(operator(HttpMethod.GET, me, "/api/v1/quality/evaluation-runs?goldenSetId={s}", setId))
        .andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, "/api/v1/quality/evaluation-runs/{id}", candidate))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-029 R-22·R-24] 영상별 단계 리포트·추이 응답과 오류 응답이 계약과 일치한다")
  void stageReports() throws Exception {
    UUID source = UUID.randomUUID();
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    Instant at = day.atTime(6, 0).toInstant(ZoneOffset.UTC);
    ingest.ingest(
        StageFixtures.batch(
            StageFixtures.timing(source, "analyzer.judge", at, 120, "ok"),
            StageFixtures.timing(source, "analyzer.judge", at, -1, "error")));
    aggregation.aggregate(day);

    perform(operator(HttpMethod.GET, me, "/api/v1/quality/stage-reports/sources/{id}", source))
        .andExpect(status().isOk());
    // 검증기는 stageId 쿼리(oneOf const 목록)를 JSON으로 읽으려다 실패한다. 추이 요청은 응답만 검증한다.
    performInvalidRequest(
            operator(
                HttpMethod.GET,
                me,
                "/api/v1/quality/stage-reports/trends?stageId=analyzer.judge&from={f}&to={t}",
                day.minusDays(1),
                day))
        .andExpect(status().isOk());
    perform(
            operator(
                HttpMethod.GET,
                me,
                "/api/v1/quality/stage-reports/sources/{id}",
                UUID.randomUUID()))
        .andExpect(status().isNotFound());
    performInvalidRequest(
            operator(
                HttpMethod.GET,
                me,
                "/api/v1/quality/stage-reports/trends"
                    + "?stageId=analyzer.judge&from=2026-02-02&to=2026-02-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-029 R-28] 긴급 알림 리포트 응답이 계약과 일치한다")
  void alertReport() throws Exception {
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    jdbc.update(
        "INSERT INTO alert_events (candidate_id, source_id, camera_id, event_type, severity,"
            + " pending_at, resolved_at, status, evidence_frame_keys)"
            + " VALUES (?, ?, ?, 'fall', 'high', now() - interval '1 day',"
            + " now() - interval '1 day' + interval '2 seconds', 'dismissed', '[]'::jsonb)",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID());

    perform(
            operator(
                HttpMethod.GET,
                me,
                "/api/v1/quality/alert-reports?from={f}&to={t}",
                day.minusDays(1),
                day.plusDays(1)))
        .andExpect(status().isOk());
    perform(
            operator(
                HttpMethod.GET, me, "/api/v1/quality/alert-reports?from=2026-01-01&to=2026-06-01"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-030 R-1·R-9] 가져오기 접수·목록·조회와 오류 응답(409·404·400)이 계약과 일치한다")
  void datasetImports() throws Exception {
    TestcontainersConfiguration.media().resetAll();
    DatasetFixtures.upload("contract", DatasetFixtures.manifest("contract", "clip-0001"));
    DatasetFixtures.mediaAnswers("clip-0001", 201);
    String body = "{\"manifestKey\":\"" + DatasetFixtures.manifestKey("contract") + "\"}";
    perform(json(HttpMethod.POST, IMPORTS, body)).andExpect(status().isAccepted());
    perform(json(HttpMethod.POST, IMPORTS, body)).andExpect(status().isConflict());
    datasets.runWorker();

    UUID importId = DatasetImportService.importIdOf(DatasetFixtures.manifestKey("contract"));
    perform(operator(HttpMethod.GET, me, IMPORTS + "/{id}", importId)).andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, IMPORTS + "?page=0&size=20")).andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, IMPORTS + "/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    performInvalidRequest(json(HttpMethod.POST, IMPORTS, "{\"manifestKey\":\"bad\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-030 R-14·R-15] 내보내기 접수·조회와 오류 응답(400·404)이 계약과 일치한다")
  void featureExports() throws Exception {
    UUID source = UUID.randomUUID();
    exportFixtures.completed(source, 1);
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    String body =
        perform(
                json(
                    HttpMethod.POST,
                    EXPORTS,
                    "{\"from\":\"" + today + "\",\"to\":\"" + today + "\"}"))
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse()
            .getContentAsString();
    exportFixtures.runWorker();
    String exportId = new ObjectMapper().readTree(body).get("exportId").asText();

    perform(operator(HttpMethod.GET, me, EXPORTS + "/{id}", exportId)).andExpect(status().isOk());
    perform(operator(HttpMethod.GET, me, EXPORTS + "/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
    perform(json(HttpMethod.POST, EXPORTS, "{\"goldenSetId\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isNotFound());
    perform(json(HttpMethod.POST, EXPORTS, "{}")).andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-030 R-16~R-18] 성능 Gate 응답과 403이 계약과 일치한다")
  void stageGate() throws Exception {
    UUID baseline = UUID.randomUUID();
    UUID candidate = UUID.randomUUID();
    Instant at = Instant.now().minusSeconds(3600);
    for (int i = 0; i < 5; i++) {
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.timing(baseline, "normalize.transcode", at, 1000, "ok"),
              StageFixtures.timing(candidate, "normalize.transcode", at, 1500, "ok"),
              StageFixtures.timing(candidate, "normalize.probe", at, 10, "ok")));
    }
    String body =
        "{\"baselineSourceIds\":[\""
            + baseline
            + "\"],\"candidateSourceIds\":[\""
            + candidate
            + "\"]}";

    perform(stageGate(body, "quality-evals")).andExpect(status().isOk());
    perform(stageGate(body, "someone-else")).andExpect(status().isForbidden());
  }

  private MockHttpServletRequestBuilder json(HttpMethod method, String path, String body) {
    return operator(method, me, path).contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private static MockHttpServletRequestBuilder stageGate(String body, String caller) {
    return MockMvcRequestBuilders.post("/internal/v1/quality/stage-gates")
        .header("X-Caller", caller)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private static MockHttpServletRequestBuilder internal(String body, String caller) {
    return MockMvcRequestBuilders.post(EvaluationFixtures.SUBMIT)
        .header("X-Caller", caller)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andExpect(matchesContract);
  }

  private ResultActions performInvalidRequest(MockHttpServletRequestBuilder request)
      throws Exception {
    return mvc.perform(request).andExpect(responseMatchesContract);
  }
}
