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
        "TRUNCATE normalized_receipts, normalized_job_plans, normalization_results, analysis_settings, chunks, jobs, dispatch_states, dispatch_users, chunk_executions, execution_slots, outbox_events RESTART IDENTITY CASCADE")
class DispatchServiceTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired NormalizedIntakeService intake;
  @Autowired NormalizedEventParser parser;
  @Autowired DispatchService dispatch;
  @Autowired JdbcTemplate jdbc;

  UUID plan(UUID owner) throws Exception {
    var e =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    Objects.requireNonNull(
                        getClass()
                            .getResourceAsStream(
                                "/contracts/events/video.normalized.v2.schema.json")))
                .get("examples")
                .get(0)
                .deepCopy();
    e.put("eventId", UUID.randomUUID().toString());
    e.put("sourceId", UUID.randomUUID().toString());
    e.put("normalizationId", UUID.randomUUID().toString());
    e.put("ownerId", owner.toString());
    String prefix =
        "sources/"
            + e.get("sourceId").asText()
            + "/normalizations/"
            + e.get("normalizationId").asText()
            + "/";
    e.put("normalizedKey", prefix + "normalized.mp4");
    e.put("hlsPlaylistKey", prefix + "hls/index.m3u8");
    return Objects.requireNonNull(
        intake
            .receive(
                parser.parse(e.get("sourceId").asText(), e.toString()),
                new AnalysisSettings("dispatch-v1", "local", "infer-v1"),
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
            .jobId());
  }

  int count(String table) {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM "
                + table
                + (table.equals("outbox_events") ? " WHERE event_type='chunk.requested'" : ""),
            Integer.class));
  }

  @Test
  @DisplayName("[SPEC-010 R-1 R-5 R-6] 정규화 연결 계획의 첫 청크를 실행·슬롯·상태·Outbox로 함께 저장한다")
  void dispatchesAtomically() throws Exception {
    UUID job = plan(UUID.randomUUID());
    assertThat(count("outbox_events")).isZero();
    UUID execution = dispatch.dispatchOne().orElseThrow();
    assertThat(count("chunk_executions")).isEqualTo(1);
    assertThat(count("execution_slots")).isEqualTo(1);
    assertThat(count("outbox_events")).isEqualTo(1);
    var row =
        jdbc.queryForMap(
            "SELECT e.execution_id,c.status,j.plan_id FROM chunk_executions e JOIN chunks c ON c.id=e.chunk_id JOIN jobs j ON j.id=c.job_id");
    assertThat(row.get("status")).isEqualTo("DISPATCHED");
    assertThat(row.get("execution_id")).isEqualTo(execution);
    assertThat(row.get("plan_id")).isEqualTo(job);
    var payload =
        new ObjectMapper()
            .readTree(
                jdbc.queryForObject(
                    "SELECT payload::text FROM outbox_events WHERE event_type='chunk.requested'",
                    String.class));
    assertThat(payload.get("executionId").asText()).isEqualTo(execution.toString());
    assertThat(payload.get("requestId")).isEqualTo(payload.get("executionId"));
    assertThat(payload.get("eventId")).isNotEqualTo(payload.get("executionId"));
    assertThat(payload.at("/ownedRange/endMs").asLong()).isEqualTo(60000);
    assertThat(payload.at("/processingRange/endMs").asLong()).isEqualTo(65000);
    assertThat(payload.at("/processingProfile/version").asText()).isEqualTo("dispatch-v1");
    assertThat(payload.at("/processingProfile/inferenceProfileVersion").asText())
        .isEqualTo("infer-v1");
  }

  @Autowired com.vlm.pipeline.domain.DispatchRepository repository;
  @Autowired com.vlm.common.messaging.OutboxRecorder outbox;
  @Autowired com.vlm.pipeline.application.JobExecutionService executions;
  @Autowired org.springframework.transaction.support.TransactionTemplate tx;
  @Autowired org.springframework.context.ApplicationContext context;

  Optional<UUID> limited(int user, int source, int global) {
    var properties =
        new com.vlm.pipeline.config.DispatchProperties(
            false, user, source, global, 300000, 1000, 100);
    return Objects.requireNonNull(
        tx.execute(
            t -> new DispatchService(repository, outbox, properties, executions).dispatchOne()));
  }

  @Test
  @DisplayName("[SPEC-010 R-2] 영상별·사용자별·전체 상한과 서로 다른 서버의 구성 충돌을 지킨다")
  void limits() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    plan(a);
    plan(a);
    plan(a);
    plan(b);
    plan(c);
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(dispatch.dispatchOne()).isEmpty();
    assertThat(jdbc.queryForList("SELECT count(*) n FROM execution_slots GROUP BY source_id"))
        .allSatisfy(r -> assertThat(((Number) r.get("n")).intValue()).isEqualTo(1));
    assertThat(jdbc.queryForList("SELECT count(*) n FROM execution_slots GROUP BY owner_id"))
        .allSatisfy(r -> assertThat(((Number) r.get("n")).intValue()).isLessThanOrEqualTo(2));
    assertThatThrownBy(() -> limited(3, 1, 5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("configuration");
    assertThat(count("execution_slots")).isEqualTo(4);
  }

  @Test
  @DisplayName("[SPEC-010 R-1 R-2] 한 영상은 상한만큼만 배정하고 legacy·비활성 계획은 제외한다")
  void sourceAndEligibility() throws Exception {
    UUID job = plan(UUID.randomUUID());
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(dispatch.dispatchOne()).isEmpty();
    var terminal = plan(UUID.randomUUID());
    jdbc.update("UPDATE jobs SET status='COMPLETED' WHERE plan_id=?", terminal);
    var legacy = plan(UUID.randomUUID());
    jdbc.update("DELETE FROM normalized_receipts WHERE job_plan_id=?", legacy);
    jdbc.update("DELETE FROM normalized_job_plans WHERE job_plan_id=?", legacy);
    assertThat(dispatch.dispatchOne()).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type='chunk.requested' AND (payload->>'jobId')::uuid=?",
                Integer.class,
                job))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-010 R-4] 새 배정 인스턴스도 사용자별 한 청크 순환 순서를 유지한다")
  void fairAcrossRestarts() throws Exception {
    UUID a = UUID.fromString("00000000-0000-4000-8000-000000000001"),
        b = UUID.fromString("00000000-0000-4000-8000-000000000002");
    for (int i = 0; i < 3; i++) {
      plan(a);
      plan(b);
    }
    for (int i = 0; i < 4; i++) assertThat(limited(2, 1, 4)).isPresent();
    assertThat(jdbc.queryForList("SELECT owner_id FROM execution_slots ORDER BY id", UUID.class))
        .containsExactly(a, b, a, b);
    assertThat(count("dispatch_users")).isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-010 R-3] 독립 배정 서버의 동시 요청도 청크를 중복 배정하거나 상한을 넘지 않는다")
  void concurrentServers() throws Exception {
    for (int i = 0; i < 8; i++) plan(UUID.randomUUID());
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var futures = new ArrayList<java.util.concurrent.Future<Optional<UUID>>>();
      for (int i = 0; i < 16; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  return limited(2, 1, 4);
                }));
      start.countDown();
      int assigned = 0;
      for (var future : futures)
        if (future.get(20, java.util.concurrent.TimeUnit.SECONDS).isPresent()) assigned++;
      assertThat(assigned).isEqualTo(4);
    }
    assertThat(count("chunk_executions")).isEqualTo(4);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT chunk_id) FROM chunk_executions", Integer.class))
        .isEqualTo(4);
    assertThat(count("execution_slots")).isEqualTo(4);
    assertThat(count("outbox_events")).isEqualTo(4);
  }

  @Test
  @DisplayName("[SPEC-010 R-13] 자동 배정과 발행은 기본 비활성이며 잘못된 상한은 거부한다")
  void defaults() {
    assertThat(context.getBean(com.vlm.pipeline.config.DispatchProperties.class).enabled())
        .isFalse();
    assertThat(context.containsBean("dispatchSchedule")).isFalse();
    assertThat(context.containsBean("outboxPublishSchedule")).isFalse();
    assertThatThrownBy(
            () -> new com.vlm.pipeline.config.DispatchProperties(true, 1, 2, 4, 1000, 1000, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new com.vlm.pipeline.config.DispatchProperties(true, 2, 1, 0, 1000, 1000, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new com.vlm.pipeline.config.DispatchProperties(true, 2, 1, 4, 0, 1000, 10))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "chunk_executions,false",
    "execution_slots,false",
    "chunks,status<>'DISPATCHED'",
    "jobs,status<>'RUNNING'",
    "dispatch_states,sequence=0",
    "dispatch_users,false",
    "outbox_events,false"
  })
  @DisplayName("[SPEC-010 R-8] 각 DB 저장 경계 실패는 전체 롤백하고 새 시도는 정상 복구한다")
  void rollback(String table, String check) throws Exception {
    plan(UUID.randomUUID());
    jdbc.execute(
        "ALTER TABLE " + table + " ADD CONSTRAINT dispatch_failure CHECK(" + check + ") NOT VALID");
    try {
      assertThatThrownBy(() -> dispatch.dispatchOne())
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(count("chunk_executions")).isZero();
      assertThat(count("execution_slots")).isZero();
      assertThat(count("outbox_events")).isZero();
      assertThat(count("dispatch_users")).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM chunks WHERE status<>'PENDING'", Integer.class))
          .isZero();
      assertThat(jdbc.queryForObject("SELECT status FROM jobs", String.class)).isEqualTo("PLANNED");
    } finally {
      jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT dispatch_failure");
    }
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(count("outbox_events")).isEqualTo(1);
  }

  @Autowired DispatchRecoveryService recovery;
  @Autowired io.micrometer.core.instrument.MeterRegistry meters;

  void expired(UUID execution) {
    jdbc.update(
        "UPDATE chunk_executions SET start_before=CURRENT_TIMESTAMP WHERE execution_id=?",
        execution);
  }

  @Test
  @DisplayName("[SPEC-010 R-10 R-11] 만료는 슬롯을 유지하고 종료 증거 확인 후 한 번만 반환한다")
  void recoversWithEvidence() throws Exception {
    plan(UUID.randomUUID());
    UUID execution = dispatch.dispatchOne().orElseThrow(), confirmation = UUID.randomUUID();
    assertThatThrownBy(() -> recovery.confirmStopped(execution, confirmation, "worker-stop:123"))
        .isInstanceOf(IllegalStateException.class);
    expired(execution);
    assertThat(meters.get("pipeline.dispatch.recovery.required").gauge().value()).isEqualTo(1);
    assertThat(recovery.markExpired()).isEqualTo(1);
    assertThat(recovery.markExpired()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isEqualTo(1);
    assertThat(dispatch.dispatchOne()).isEmpty();
    recovery.confirmStopped(execution, confirmation, "worker-stop:123");
    recovery.confirmStopped(execution, confirmation, "worker-stop:123");
    assertThat(jdbc.queryForObject("SELECT status FROM chunk_executions", String.class))
        .isEqualTo("STOPPED");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM chunks WHERE status='FAILED'", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isZero();
    assertThat(recovery.recoveryCount()).isZero();
    assertThat(dispatch.dispatchOne()).isPresent();
    assertThat(count("chunk_executions")).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT chunk_id) FROM chunk_executions", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-010 R-12] 다른 확인 ID·다른 증거·다른 실행의 확인 재사용을 거부한다")
  void stopConflicts() throws Exception {
    plan(UUID.randomUUID());
    plan(UUID.randomUUID());
    UUID first = dispatch.dispatchOne().orElseThrow(),
        second = dispatch.dispatchOne().orElseThrow(),
        confirmation = UUID.randomUUID();
    expired(first);
    expired(second);
    recovery.confirmStopped(first, confirmation, "worker-stop:1");
    assertThatThrownBy(() -> recovery.confirmStopped(first, UUID.randomUUID(), "worker-stop:1"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> recovery.confirmStopped(first, confirmation, "worker-stop:2"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> recovery.confirmStopped(second, confirmation, "worker-stop:1"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> recovery.confirmStopped(UUID.randomUUID(), UUID.randomUUID(), "worker-stop:3"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> recovery.confirmStopped(second, UUID.randomUUID(), " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chunk_executions WHERE status='STOPPED'", Integer.class))
        .isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "chunk_executions,status<>'STOPPED'",
    "execution_slots,released_at IS NULL",
    "chunks,status<>'FAILED'"
  })
  @DisplayName("[SPEC-010 R-11] 회수 저장 실패도 종료 기록·청크·슬롯을 모두 롤백한다")
  void recoveryRollback(String table, String check) throws Exception {
    plan(UUID.randomUUID());
    UUID execution = dispatch.dispatchOne().orElseThrow(), confirmation = UUID.randomUUID();
    expired(execution);
    recovery.markExpired();
    jdbc.execute(
        "ALTER TABLE " + table + " ADD CONSTRAINT recovery_failure CHECK(" + check + ") NOT VALID");
    try {
      assertThatThrownBy(() -> recovery.confirmStopped(execution, confirmation, "stop:verified"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(jdbc.queryForObject("SELECT status FROM chunk_executions", String.class))
          .isEqualTo("RECOVERY_REQUIRED");
      assertThat(jdbc.queryForObject("SELECT confirmation_id FROM chunk_executions", UUID.class))
          .isNull();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM chunks WHERE status='DISPATCHED'", Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT recovery_failure");
    }
    recovery.confirmStopped(execution, confirmation, "stop:verified");
    assertThat(recovery.recoveryCount()).isZero();
  }

  @Test
  @DisplayName("[SPEC-010 R-3 R-11] 동시에 같은 종료 확인을 수행해도 슬롯 반환은 한 번이다")
  void concurrentRecovery() throws Exception {
    plan(UUID.randomUUID());
    UUID execution = dispatch.dispatchOne().orElseThrow(), confirmation = UUID.randomUUID();
    expired(execution);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var futures = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 8; i++)
        futures.add(
            pool.submit(() -> recovery.confirmStopped(execution, confirmation, "stop:concurrent")));
      for (var f : futures) f.get(20, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chunk_executions WHERE status='STOPPED'", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-010 R-2] 모든 슬롯 반환 뒤에는 새 배정 상한 구성으로 전환할 수 있다")
  void reconfigureWhenDrained() throws Exception {
    plan(UUID.randomUUID());
    UUID execution = dispatch.dispatchOne().orElseThrow();
    expired(execution);
    recovery.confirmStopped(execution, UUID.randomUUID(), "stop:drained");
    assertThat(limited(3, 2, 5)).isPresent();
    assertThat(jdbc.queryForObject("SELECT global_limit FROM dispatch_states", Integer.class))
        .isEqualTo(5);
  }

  @Test
  @DisplayName("[SPEC-010 R-1 R-6] 마지막 나머지 구간과 확장 경계를 반올림 없이 정수 ms로 발행한다")
  void preciseRanges() throws Exception {
    plan(UUID.randomUUID());
    for (int i = 0; i < 3; i++) assertThat(limited(3, 3, 3)).isPresent();
    var payload =
        new ObjectMapper()
            .readTree(
                jdbc.queryForObject(
                    "SELECT payload::text FROM outbox_events WHERE event_type='chunk.requested' ORDER BY id DESC LIMIT 1",
                    String.class));
    assertThat(payload.at("/ownedRange/startMs").asLong()).isEqualTo(120000);
    assertThat(payload.at("/ownedRange/endMs").asLong()).isEqualTo(125001);
    assertThat(payload.at("/processingRange/startMs").asLong()).isEqualTo(115000);
    assertThat(payload.at("/processingRange/endMs").asLong()).isEqualTo(125001);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT payload->>'chunkId') FROM outbox_events WHERE event_type='chunk.requested'",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  @DisplayName("[SPEC-010 R-8] ms로 정확히 변환되지 않는 손상 구간은 배정 전체를 롤백한다")
  void refusesRounding() throws Exception {
    plan(UUID.randomUUID());
    jdbc.update("UPDATE chunks SET owned_start=0.0001 WHERE ordinal=0");
    assertThatThrownBy(() -> dispatch.dispatchOne()).isInstanceOf(ArithmeticException.class);
    assertThat(count("outbox_events")).isZero();
    assertThat(count("execution_slots")).isZero();
    assertThat(count("chunk_executions")).isZero();
  }

  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
  com.vlm.pipeline.infra.persistence.JdbcDispatchRepository persistence;

  @Test
  @DisplayName("[SPEC-010 R-8] Outbox 기록 뒤 trace 보존 실패도 모든 변경을 롤백한다")
  void rollbackAfterOutbox() throws Exception {
    plan(UUID.randomUUID());
    org.mockito.Mockito.doAnswer(
            call -> {
              call.callRealMethod();
              throw new IllegalStateException("injected after outbox");
            })
        .when(persistence)
        .preserveTrace(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.nullable(String.class));
    assertThatThrownBy(() -> dispatch.dispatchOne()).isInstanceOf(IllegalStateException.class);
    assertThat(count("outbox_events")).isZero();
    assertThat(count("execution_slots")).isZero();
    assertThat(count("chunk_executions")).isZero();
    assertThat(count("dispatch_users")).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chunks WHERE status<>'PENDING'", Integer.class))
        .isZero();
    org.mockito.Mockito.doCallRealMethod()
        .when(persistence)
        .preserveTrace(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.nullable(String.class));
    assertThat(dispatch.dispatchOne()).isPresent();
  }

  @Test
  @DisplayName("[SPEC-010 R-1] 기존 데이터에 추가 마이그레이션을 적용해도 PK와 소수 초 구간을 보존한다")
  void expandsExistingData() throws Exception {
    String schema = "dispatch_migration";
    var flyway =
        org.flywaydb.core.Flyway.configure()
            .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
            .schemas(schema)
            .defaultSchema(schema)
            .target("202611031500")
            .load();
    try {
      flyway.migrate();
      jdbc.update(
          "INSERT INTO dispatch_migration.jobs(plan_id,source_id,duration,profile,chunk_length,overlap,status) VALUES(?,?,125.001,'LOCAL',60,5,'PLANNED')",
          UUID.randomUUID(),
          UUID.randomUUID());
      jdbc.update(
          "INSERT INTO dispatch_migration.chunks(job_id,ordinal,owned_start,owned_end,expanded_start,expanded_end) VALUES(1,0,0,60,0,65)");
      org.flywaydb.core.Flyway.configure()
          .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      var row =
          jdbc.queryForMap("SELECT id,chunk_id,owned_end,status FROM dispatch_migration.chunks");
      assertThat(row.get("id")).isEqualTo(1L);
      assertThat(row.get("chunk_id")).isInstanceOf(UUID.class);
      assertThat(row.get("owned_end")).isEqualTo(new java.math.BigDecimal("60"));
      assertThat(row.get("status")).isEqualTo("PENDING");
    } finally {
      jdbc.execute("DROP SCHEMA dispatch_migration CASCADE");
    }
  }
}
