package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.vlm.pipeline.application.JobExecutionService;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class JobExecutionTest extends ExecutionResultTest {
  @Autowired JobExecutionService jobs;
  @Autowired org.springframework.test.web.servlet.MockMvc mvc;
  @Autowired com.vlm.pipeline.domain.DispatchRepository dispatchRepository;
  @Autowired com.vlm.pipeline.domain.JobExecutionRepository executionRepository;
  @Autowired com.vlm.common.messaging.OutboxRecorder outbox;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;

  @Test
  @DisplayName("[SPEC-015 R-1 R-2 R-3 R-11] 전체 성공 실행을 분석 완료와 구분한다")
  void allSucceeded() throws Exception {
    UUID owner = UUID.randomUUID();
    UUID job = plan(owner);
    for (int i = 0; i < jobs.status(job, owner).path("execution").path("total").asInt(); i++)
      receive(result(dispatch.dispatchOne().orElseThrow(), true));
    var view = jobs.status(job, owner);
    assertThat(view.path("execution").path("status").asText()).isEqualTo("SUCCEEDED");
    assertThat(view.path("execution").path("progressPercent").asInt()).isEqualTo(100);
    assertThat(view.path("status").asText()).isEqualTo("RUNNING");
  }

  int number(UUID job, UUID owner, String field) {
    return jobs.status(job, owner).path("execution").path(field).asInt();
  }

  @Test
  @DisplayName("[SPEC-015 R-3] 성공과 최종 실패 혼합을 부분 실패로 집계한다")
  void partialFailure() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    int total = number(job, owner, "total");
    for (int i = 0; i < total; i++) receive(result(dispatch.dispatchOne().orElseThrow(), i == 0));
    var view = jobs.status(job, owner);
    assertThat(view.path("execution").path("status").asText()).isEqualTo("PARTIAL_FAILED");
    assertThat(number(job, owner, "failed")).isEqualTo(total - 1);
    assertThat(view.path("failures")).hasSize(total - 1);
    assertThat(number(job, owner, "successPercent")).isEqualTo(100 / total);
  }

  @Test
  @DisplayName("[SPEC-015 R-3] 모든 확정 실패를 전체 실행 실패로 집계한다")
  void allFailed() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    int total = number(job, owner, "total");
    for (int i = 0; i < total; i++) receive(result(dispatch.dispatchOne().orElseThrow(), false));
    assertThat(jobs.status(job, owner).path("execution").path("status").asText())
        .isEqualTo("FAILED");
    assertThat(number(job, owner, "progressPercent")).isEqualTo(100);
  }

  @Test
  @DisplayName("[SPEC-015 R-5 R-6] 재시도는 기한과 예산을 지키고 새 실행 식별자로 배정한다")
  void retryBudget() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    jdbc.update(
        "UPDATE jobs SET max_attempts=2,retryable_error_codes=ARRAY['PROCESSING_FAILED'],retry_delay_ms=60000");
    // 한 청크만 남기는 대신 다른 청크도 먼저 종료하여 실제 계획의 분모를 보존한다.
    UUID first = dispatch.dispatchOne().orElseThrow();
    var failure = result(first, false);
    receive(failure);
    assertThat(number(job, owner, "retryPending")).isEqualTo(1);
    assertThat(number(job, owner, "failed")).isZero();
    for (int i = 1; i < number(job, owner, "total"); i++)
      receive(result(dispatch.dispatchOne().orElseThrow(), true));
    assertThat(dispatch.dispatchOne()).isEmpty();
    assertThat(jobs.status(job, owner).path("execution").path("terminal").asBoolean()).isFalse();
    jdbc.update(
        "UPDATE chunks SET retry_at=now()-interval '1 second' WHERE status='RETRY_PENDING'");
    UUID second = dispatch.dispatchOne().orElseThrow();
    assertThat(second).isNotEqualTo(first);
    var before = jobs.status(job, owner);
    receive(failure);
    assertThat(jobs.status(job, owner)).isEqualTo(before);
    receive(result(second, false));
    assertThat(jobs.status(job, owner).path("execution").path("status").asText())
        .isEqualTo("PARTIAL_FAILED");
    assertThat(number(job, owner, "failed")).isEqualTo(1);
    assertThat(jobs.status(job, owner).path("failures")).hasSize(2);
  }

  @Test
  @DisplayName("[SPEC-015 R-4 R-12] 기한 경과는 성공·종료·슬롯 반환이 아니다")
  void unknownExecution() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    UUID execution = dispatch.dispatchOne().orElseThrow();
    jdbc.update(
        "UPDATE chunk_executions SET start_before=now()-interval '1 second' WHERE execution_id=?",
        execution);
    recovery.markExpired();
    var view = jobs.status(job, owner);
    assertThat(view.path("execution").path("status").asText()).isEqualTo("UNKNOWN");
    assertThat(view.path("execution").path("terminal").asBoolean()).isFalse();
    assertThat(held()).isEqualTo(1);
    assertThat(view.path("issues").get(0).path("errorCode").asText())
        .isEqualTo("START_DEADLINE_ELAPSED");
    recovery.confirmStopped(execution, UUID.randomUUID(), "ticket:verified-stop");
    assertThat(number(job, owner, "unknown")).isZero();
    assertThat(number(job, owner, "failed")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-015 R-7 R-8] 중복 결과와 변경 없는 점검은 순번을 늘리지 않는다")
  void stableSnapshot() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    var e = result(dispatch.dispatchOne().orElseThrow(), true);
    receive(e);
    var before = jobs.status(job, owner);
    receive(e);
    jobs.reconcile(100);
    assertThat(jobs.status(job, owner)).isEqualTo(before);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='analysis.completed'",
                Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[SPEC-015 R-8] Outbox 저장 실패는 청크·슬롯·집계·수신 기록 모두 롤백한다")
  void aggregationRollback() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    UUID execution = dispatch.dispatchOne().orElseThrow();
    var e = result(execution, true);
    var before = jobs.status(job, owner);
    jdbc.execute(
        "ALTER TABLE outbox_events ADD CONSTRAINT status_failure CHECK(event_type<>'job.status') NOT VALID");
    try {
      assertThatThrownBy(() -> receive(e))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      jdbc.execute("ALTER TABLE outbox_events DROP CONSTRAINT status_failure");
    }
    assertThat(jobs.status(job, owner)).isEqualTo(before);
    assertThat(held()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isZero();
    receive(e);
    assertThat(number(job, owner, "succeeded")).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-015 R-9] 재시작 뒤 누락 집계를 새 점검기로 복구하고 순번을 보존한다")
  void reconcileMissing() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    receive(result(dispatch.dispatchOne().orElseThrow(), true));
    var before = jobs.status(job, owner);
    jdbc.execute("DELETE FROM job_execution_summaries");
    tx.executeWithoutResult(
        t ->
            new JobExecutionService(
                    dispatchRepository,
                    new com.vlm.pipeline.infra.persistence.JdbcJobExecutionRepository(
                        jdbc, new com.fasterxml.jackson.databind.ObjectMapper()),
                    outbox)
                .reconcile(1));
    var after = jobs.status(job, owner);
    assertThat(after.path("execution")).isEqualTo(before.path("execution"));
    assertThat(after.path("sequence").asLong()).isGreaterThan(before.path("sequence").asLong());
  }

  @Test
  @DisplayName("[SPEC-015 R-10] 소유자만 조회하며 타인과 없는 Job은 동일 404다")
  void ownerApi() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/jobs/{job}/execution", job)
                .header("X-User-Id", owner.toString()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                    "$.execution.status")
                .value("PENDING"));
    for (UUID id : java.util.List.of(job, UUID.randomUUID()))
      mvc.perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                      "/api/v1/jobs/{job}/execution", id)
                  .header("X-User-Id", UUID.randomUUID().toString()))
          .andExpect(
              org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                  .isNotFound());
  }

  @Test
  @DisplayName("[SPEC-015 R-7 R-8 NFR-1] 여러 서버의 역순 마지막 결과는 한 번만 실행 종료한다")
  void concurrentLastResults() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    int total = number(job, owner, "total");
    var custom =
        new com.vlm.pipeline.application.DispatchService(
            dispatchRepository,
            outbox,
            new com.vlm.pipeline.config.DispatchProperties(false, 3, 3, 4, 300000, 1000, 100),
            jobs);
    var events = new java.util.ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
    for (int i = 0; i < total; i++)
      events.add(
          result(
              java.util.Objects.requireNonNull(tx.execute(t -> custom.dispatchOne())).orElseThrow(),
              true));
    java.util.Collections.reverse(events);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(total)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
      for (var e : events)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return receive(e);
                }));
      start.countDown();
      for (var f : futures)
        assertThat(f.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("APPLIED");
    }
    assertThat(number(job, owner, "succeeded")).isEqualTo(total);
    assertThat(held()).isZero();
    assertThat(jobs.status(job, owner).path("execution").path("terminal").asBoolean()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='job.status' AND payload->'execution'->>'terminal'='true'",
                Integer.class))
        .isEqualTo(1);
    var before = jobs.status(job, owner);
    for (var e : events) {
      e.put("eventId", UUID.randomUUID().toString());
      receive(e);
    }
    assertThat(jobs.status(job, owner)).isEqualTo(before);
  }

  @Test
  @DisplayName("[SPEC-015 R-8] 집계 저장 실패도 전체 종료 반영을 롤백한다")
  void summaryRollback() throws Exception {
    UUID owner = UUID.randomUUID(),
        job = plan(owner),
        execution = dispatch.dispatchOne().orElseThrow();
    var e = result(execution, true);
    var before = jobs.status(job, owner);
    jdbc.execute(
        "ALTER TABLE job_execution_summaries ADD CONSTRAINT summary_failure CHECK(false) NOT VALID");
    try {
      assertThatThrownBy(() -> receive(e))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      jdbc.execute("ALTER TABLE job_execution_summaries DROP CONSTRAINT summary_failure");
    }
    assertThat(jobs.status(job, owner)).isEqualTo(before);
    assertThat(held()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[SPEC-015 R-9 NFR-3] 점검 배치는 누락·불일치를 순환 복구하며 기존 조회를 보존한다")
  void boundedReconciliation() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), ja = plan(a), jb = plan(b);
    jdbc.execute("DELETE FROM job_execution_summaries");
    assertThat(jobs.reconcile(1)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM job_execution_summaries", Integer.class))
        .isEqualTo(1);
    jobs.reconcile(1);
    assertThat(jobs.status(ja, a).path("jobId").asText()).isEqualTo(ja.toString());
    assertThat(jobs.status(jb, b).path("jobId").asText()).isEqualTo(jb.toString());
    jdbc.execute(
        "UPDATE job_execution_summaries SET snapshot=jsonb_set(snapshot,'{execution,total}','999')");
    jobs.reconcile(100);
    assertThat(number(ja, a, "total")).isEqualTo(3);
    assertThatThrownBy(() -> jobs.reconcile(1001)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("[SPEC-015 R-10] 누락·오류 사용자 헤더는 401이고 잘못된 Job ID는 400이다")
  void invalidApiInput() throws Exception {
    UUID job = plan(UUID.randomUUID());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/v1/jobs/{job}/execution", job))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/jobs/{job}/execution", job)
                .header("X-User-Id", "invalid"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                    "/api/v1/jobs/not-a-uuid/execution")
                .header("X-User-Id", UUID.randomUUID().toString()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isBadRequest());
  }

  @Test
  @DisplayName("[SPEC-015 R-9 R-10] 과거 누락 집계는 소유자 조회만 복구하고 타인 조회는 변경하지 않는다")
  void authorizedReadRepair() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    var prior = jobs.status(job, owner);
    jdbc.execute("DELETE FROM job_execution_summaries");
    long count =
        java.util.Objects.requireNonNull(
            jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class));
    assertThatThrownBy(() -> jobs.status(job, UUID.randomUUID()))
        .isInstanceOf(com.vlm.common.error.BusinessException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Long.class))
        .isEqualTo(count);
    var recovered = jobs.status(job, owner);
    assertThat(recovered.path("execution")).isEqualTo(prior.path("execution"));
    assertThat(recovered.path("sequence").asLong()).isGreaterThan(prior.path("sequence").asLong());
  }

  @Test
  @DisplayName("[SPEC-015 R-5] 새 Job 정책은 생성 시 저장하고 재요청·설정 교체로 덮어쓰지 않는다")
  void immutableRetryPolicy() {
    var first =
        new com.vlm.pipeline.config.JobExecutionProperties(
            false, 30000, 100, 2, java.util.List.of("PROCESSING_FAILED"), 1000);
    var second =
        new com.vlm.pipeline.config.JobExecutionProperties(
            false, 30000, 100, 4, java.util.List.of("PROCESSING_FAILED"), 2000);
    UUID id = UUID.randomUUID();
    var input =
        new com.vlm.pipeline.domain.PlanInput(
            id,
            UUID.randomUUID(),
            new java.math.BigDecimal("10"),
            "local",
            new java.math.BigDecimal("60"),
            new java.math.BigDecimal("1"));
    tx.executeWithoutResult(
        t ->
            new com.vlm.pipeline.application.PlanningService(
                    new com.vlm.pipeline.infra.persistence.JdbcPlanRepository(jdbc, first))
                .create(input));
    tx.executeWithoutResult(
        t ->
            new com.vlm.pipeline.application.PlanningService(
                    new com.vlm.pipeline.infra.persistence.JdbcPlanRepository(jdbc, second))
                .create(input));
    assertThat(
            jdbc.queryForObject("SELECT max_attempts FROM jobs WHERE plan_id=?", Integer.class, id))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject("SELECT retry_delay_ms FROM jobs WHERE plan_id=?", Long.class, id))
        .isEqualTo(1000);
    assertThatThrownBy(
            () ->
                new com.vlm.pipeline.config.JobExecutionProperties(
                    false, 30000, 100, 0, java.util.List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("[SPEC-015 R-8 R-10] 실제 API와 Outbox가 확정 JSON 계약을 따른다")
  void snapshotContract() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    receive(result(dispatch.dispatchOne().orElseThrow(), false));
    var factory =
        com.networknt.schema.JsonSchemaFactory.getInstance(
            com.networknt.schema.SpecVersion.VersionFlag.V202012,
            b ->
                b.schemaMappers(
                    m ->
                        m.mapPrefix(
                            "https://vlm.example/contracts/events/",
                            "classpath:contracts/events/")));
    var schema =
        factory.getSchema(
            com.networknt.schema.SchemaLocation.of(
                "https://vlm.example/contracts/events/job.status.schema.json"));
    for (String payload :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_events WHERE event_type='job.status'", String.class))
      assertThat(
              schema.validate(new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload)))
          .isEmpty();
    var response =
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/v1/jobs/{job}/execution", job)
                    .header("X-User-Id", owner.toString()))
            .andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var snapshot = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response);
    assertThat(snapshot).isEqualTo(jobs.status(job, owner));
    assertThat(snapshot.has("execution")).isTrue();
    assertThat(snapshot.has("sequence")).isTrue();
  }

  @org.junit.jupiter.api.RepeatedTest(3)
  @DisplayName("[SPEC-015 R-7 R-8 R-9 NFR-1] 마지막 결과·복구 CLI·점검 경쟁도 종료 집계와 슬롯을 한 번만 바꾼다")
  void terminalRecoveryRace() throws Exception {
    UUID owner = UUID.randomUUID(), job = plan(owner);
    int total = number(job, owner, "total");
    for (int n = 1; n < total; n++) receive(result(dispatch.dispatchOne().orElseThrow(), true));
    UUID execution = dispatch.dispatchOne().orElseThrow();
    var e = result(execution, true);
    jdbc.update(
        "UPDATE chunk_executions SET start_before=now()-interval '1 second' WHERE execution_id=?",
        execution);
    recovery.markExpired();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(3)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                receive(e);
                return true;
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                recovery.confirmStopped(execution, UUID.randomUUID(), "ticket:verified-stop");
                return true;
              });
      var c =
          pool.submit(
              () -> {
                start.await();
                jobs.reconcile(100);
                return true;
              });
      start.countDown();
      assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(b.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      assertThat(c.get(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    // 조회의 보정 기능으로 오류를 가리지 않도록 저장된 스냅샷을 직접 검증한다.
    var snapshot =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(
                jdbc.queryForObject(
                    "SELECT s.snapshot::text FROM job_execution_summaries s JOIN jobs j ON j.id=s.job_id WHERE j.plan_id=?",
                    String.class,
                    job));
    assertThat(snapshot.path("execution").path("terminal").asBoolean()).isTrue();
    assertThat(snapshot.path("execution").path("progressPercent").asInt()).isEqualTo(100);
    assertThat(
            snapshot.path("execution").path("succeeded").asInt()
                + snapshot.path("execution").path("failed").asInt())
        .isEqualTo(total);
    assertThat(held()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='job.status' AND payload->'execution'->>'terminal'='true'",
                Integer.class))
        .isEqualTo(1);
  }
}
