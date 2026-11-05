package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.pipeline.api.NormalizedEventParser;
import com.vlm.pipeline.application.*;
import com.vlm.pipeline.domain.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@Testcontainers
@Sql(
    statements =
        "TRUNCATE normalized_receipts, normalized_job_plans, normalization_results, analysis_settings, chunks, jobs RESTART IDENTITY CASCADE")
class NormalizedIntakeTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired NormalizedIntakeService intake;
  @Autowired PlanningService planning;
  @Autowired JdbcTemplate jdbc;
  @Autowired NormalizedEventParser parser;
  final AnalysisSettings settings =
      new AnalysisSettings("test-profile-v1", "local", "test-inference-v1");

  ObjectNode ready() throws Exception {
    var mapper = new ObjectMapper();
    var schema =
        mapper.readTree(
            Objects.requireNonNull(
                getClass()
                    .getResourceAsStream("/contracts/events/video.normalized.v2.schema.json")));
    var event = (ObjectNode) schema.get("examples").get(0).deepCopy();
    event.put("eventId", UUID.randomUUID().toString());
    event.put("sourceId", UUID.randomUUID().toString());
    event.put("normalizationId", UUID.randomUUID().toString());
    keys(event);
    return event;
  }

  void keys(ObjectNode event) {
    String prefix =
        "sources/"
            + event.get("sourceId").asText()
            + "/normalizations/"
            + event.get("normalizationId").asText()
            + "/";
    event.put("normalizedKey", prefix + "normalized.mp4");
    event.put("hlsPlaylistKey", prefix + "hls/index.m3u8");
  }

  NormalizedVideo parse(ObjectNode event) {
    return parser.parse(event.get("sourceId").asText(), event.toString());
  }

  @Test
  @DisplayName("[SPEC-008 R-2 R-3 R-4 R-12] 정상 수신이 기록과 계획을 원자적으로 보존하고 ms를 정확히 변환한다")
  void normal() throws Exception {
    var event = parse(ready());
    var result = intake.receive(event, settings, null);
    assertThat(result.jobId()).isNotNull();
    var job = planning.find(Objects.requireNonNull(result.jobId())).orElseThrow();
    assertThat(job.input().duration()).isEqualByComparingTo("125.001");
    assertThat(job.chunks()).hasSize(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM normalized_receipts", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-008 R-5 R-8] 같은 요청 재전달과 새 eventId의 동일 결과는 기존 Job을 재사용한다")
  void duplicates() throws Exception {
    var json = ready();
    var e = parse(json);
    var first = intake.receive(e, settings, null);
    assertThat(
            intake.receive(
                e,
                new AnalysisSettings("new-runtime-version", "production", "new-inference"),
                null))
        .isEqualTo(first);
    json.put("eventId", UUID.randomUUID().toString());
    assertThat(intake.receive(parse(json), settings, null).jobId()).isEqualTo(first.jobId());
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(2);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"height", "occurredAt", "producedAt"})
  @DisplayName("[SPEC-008 R-6] 같은 eventId의 변경 입력은 덮어쓰지 않고 충돌한다")
  void eventConflict(String field) throws Exception {
    var json = ready();
    intake.receive(parse(json), settings, null);
    if (field.equals("height")) json.put(field, 720);
    else if (field.equals("producedAt")) json.put(field, "2026-11-05T01:00:00Z");
    else
      json.put(field, java.time.Instant.parse(json.get(field).asText()).plusSeconds(1).toString());
    assertThatThrownBy(() -> intake.receive(parse(json), settings, null))
        .isInstanceOfSatisfying(
            com.vlm.common.error.BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(IntakeError.EVENT_CONFLICT));
    assertThat(count("normalized_receipts")).isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"height", "ownerId", "normalizationVersion", "normalizedKey"})
  @DisplayName("[SPEC-008 R-6] 같은 정규화 ID의 변경 메타데이터는 새 메시지라도 충돌한다")
  void normalizationConflict(String field) throws Exception {
    var json = ready();
    intake.receive(parse(json), settings, null);
    json.put("eventId", UUID.randomUUID().toString());
    switch (field) {
      case "height" -> json.put(field, 720);
      case "ownerId" -> json.put(field, UUID.randomUUID().toString());
      case "normalizationVersion" -> json.put(field, "normalize-v2");
      default -> json.put(field, json.get(field).asText().replace("normalized.mp4", "changed.mp4"));
    }
    assertThatThrownBy(() -> intake.receive(parse(json), settings, null))
        .isInstanceOfSatisfying(
            com.vlm.common.error.BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(IntakeError.NORMALIZATION_CONFLICT));
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-008 R-6] 같은 분석 설정 버전의 다른 정의는 충돌한다")
  void settingsConflict() throws Exception {
    var json = ready();
    intake.receive(parse(json), settings, null);
    json.put("eventId", UUID.randomUUID().toString());
    assertThatThrownBy(
            () ->
                intake.receive(
                    parse(json),
                    new AnalysisSettings(settings.version(), "production", "different"),
                    null))
        .isInstanceOfSatisfying(
            com.vlm.common.error.BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(IntakeError.SETTINGS_CONFLICT));
  }

  @Test
  @DisplayName("[SPEC-008 R-7] 다른 정규화 결과도 같은 영상의 활성 Job 제한을 지킨다")
  void activeJob() throws Exception {
    var json = ready();
    intake.receive(parse(json), settings, null);
    json.put("eventId", UUID.randomUUID().toString());
    json.put("normalizationId", UUID.randomUUID().toString());
    keys(json);
    assertThatThrownBy(() -> intake.receive(parse(json), settings, null))
        .isInstanceOfSatisfying(
            com.vlm.common.error.BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PlanningError.ACTIVE_JOB_EXISTS));
    assertThat(count("normalization_results")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-008 R-5] 종료 후 다른 설정은 새 계획이며 원래 설정 재수신은 기존 계획이다")
  void settingsAfterTerminal() throws Exception {
    var json = ready();
    var first = intake.receive(parse(json), settings, null);
    jdbc.update("UPDATE jobs SET status='COMPLETED'");
    json.put("eventId", UUID.randomUUID().toString());
    var second =
        intake.receive(
            parse(json), new AnalysisSettings("other", "production", "other-inference"), null);
    assertThat(second.jobId()).isNotEqualTo(first.jobId());
    json.put("eventId", UUID.randomUUID().toString());
    assertThat(intake.receive(parse(json), settings, null).jobId()).isEqualTo(first.jobId());
    assertThat(count("jobs")).isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-008 R-7] legacy 활성 계획에는 정규화 ID를 추정 연결하지 않는다")
  void legacy() throws Exception {
    var json = ready();
    var e = parse(json);
    var old = UUID.randomUUID();
    planning.create(PlanInput.local(old, e.sourceId(), new java.math.BigDecimal("125.001")));
    assertThatThrownBy(() -> intake.receive(e, settings, null))
        .isInstanceOf(com.vlm.common.error.BusinessException.class);
    assertThat(count("normalization_results")).isZero();
    assertThat(count("normalized_receipts")).isZero();
    jdbc.update("UPDATE jobs SET status='COMPLETED'");
    var now = intake.receive(e, settings, null);
    assertThat(now.jobId()).isNotEqualTo(old);
    assertThat(count("jobs")).isEqualTo(2);
    assertThat(count("normalized_job_plans")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-008 R-13] REJECTED는 수신 사실만 저장하고 반복돼도 계획하지 않는다")
  void rejected() throws Exception {
    var json = ready();
    json.put("result", "REJECTED");
    json.put("rejectReason", "NO_VIDEO_STREAM");
    for (String field :
        List.of(
            "normalizedKey",
            "hlsPlaylistKey",
            "durationMs",
            "durationSec",
            "fps",
            "width",
            "height",
            "transcoded")) json.putNull(field);
    var e = parse(json);
    assertThat(intake.receive(e, settings, null).jobId()).isNull();
    intake.receive(e, settings, null);
    assertThat(count("normalized_receipts")).isEqualTo(1);
    assertThat(count("normalization_results")).isEqualTo(1);
    assertThat(count("jobs")).isZero();
    assertThat(count("analysis_settings")).isZero();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {
        "normalization_results",
        "analysis_settings",
        "jobs",
        "chunks",
        "normalized_job_plans",
        "normalized_receipts"
      })
  @DisplayName("[SPEC-008 R-3 R-9] 각 저장 지점 실패 시 전체 신규 저장이 롤백되고 재시도가 성공한다")
  void rollback(String table) throws Exception {
    var e = parse(ready());
    String constraint = table.equals("chunks") ? "ordinal <> 1" : "false";
    jdbc.execute(
        "ALTER TABLE " + table + " ADD CONSTRAINT test_intake_failure CHECK (" + constraint + ")");
    try {
      assertThatThrownBy(() -> intake.receive(e, settings, null))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      for (String t :
          List.of(
              "normalization_results",
              "analysis_settings",
              "jobs",
              "chunks",
              "normalized_job_plans",
              "normalized_receipts")) assertThat(count(t)).as(t).isZero();
    } finally {
      jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT test_intake_failure");
    }
    assertThat(intake.receive(e, settings, null).jobId()).isNotNull();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  @DisplayName("[SPEC-008 R-11] 동시 수신은 동일/다른 eventId라도 논리적 Job 하나로 수렴한다")
  void concurrent(boolean sameEvent) throws Exception {
    var json = ready();
    var a = parse(json);
    if (!sameEvent) json.put("eventId", UUID.randomUUID().toString());
    var b = parse(json);
    var start = new java.util.concurrent.CountDownLatch(1);
    var ready = new java.util.concurrent.CountDownLatch(2);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var fs =
          List.of(a, b).stream()
              .map(
                  e ->
                      pool.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS))
                              throw new IllegalStateException("Start timed out");
                            return intake.receive(e, settings, null);
                          }))
              .toList();
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(fs.get(0).get(20, java.util.concurrent.TimeUnit.SECONDS).jobId())
          .isEqualTo(fs.get(1).get(20, java.util.concurrent.TimeUnit.SECONDS).jobId());
    }
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(sameEvent ? 1 : 2);
  }

  @Test
  @DisplayName("[SPEC-008 R-12] 운영 상한 초과는 부분 저장 없이 거절한다")
  void durationLimit() throws Exception {
    var json = ready();
    json.put("durationMs", 14400001);
    json.put("durationSec", new java.math.BigDecimal("14400.001"));
    assertThatThrownBy(() -> intake.receive(parse(json), settings, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(count("jobs")).isZero();
  }

  int count(String table) {
    return Objects.requireNonNull(
        jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class));
  }

  @Test
  @DisplayName("[SPEC-008 R-5] 새 이벤트의 발행 시각은 불변 정규화 메타데이터 비교에서 제외한다")
  void envelopePublicationDoesNotChangeNormalization() throws Exception {
    var json = ready();
    json.put("producedAt", "2026-11-05T01:00:00Z");
    var first = intake.receive(parse(json), settings, null);
    json.put("eventId", UUID.randomUUID().toString());
    json.put("producedAt", "2026-11-05T02:00:00Z");
    assertThat(intake.receive(parse(json), settings, null).jobId()).isEqualTo(first.jobId());
    assertThat(count("jobs")).isEqualTo(1);
    assertThat(count("normalized_receipts")).isEqualTo(2);
  }
}
