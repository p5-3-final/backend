package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.pipeline.api.*;
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
        "TRUNCATE normalized_receipts,normalized_job_plans,normalization_results,analysis_settings,chunks,jobs,dispatch_states,dispatch_users,chunk_executions,execution_slots,outbox_events,execution_result_receipts RESTART IDENTITY CASCADE")
class ExecutionResultTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired NormalizedIntakeService intake;
  @Autowired NormalizedEventParser parser;
  @Autowired DispatchService dispatch;
  @Autowired DispatchRecoveryService recovery;
  @Autowired ExecutionResultService results;
  @Autowired ExecutionResultParser resultParser;
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

  ObjectNode result(UUID execution, boolean success) throws Exception {
    var e =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    jdbc.queryForObject(
                        "SELECT payload::text FROM outbox_events WHERE (payload->>'executionId')::uuid=?",
                        String.class,
                        execution));
    for (String field :
        List.of(
            "normalizedKey", "ownedRange", "processingRange", "processingProfile", "startBefore"))
      e.remove(field);
    e.put("eventType", "chunk.execution.finished");
    e.put("eventId", UUID.randomUUID().toString());
    e.put("outcome", success ? "SUCCEEDED" : "FAILED");
    if (success)
      e.put(
          "resultKey",
          "sources/"
              + e.get("sourceId").asText()
              + "/jobs/"
              + e.get("jobId").asText()
              + "/chunks/"
              + e.get("chunkId").asText()
              + "/executions/"
              + execution
              + "/result.json");
    else e.putNull("resultKey");
    if (success) e.putNull("errorCode");
    else e.put("errorCode", "PROCESSING_FAILED");
    return e;
  }

  String receive(ObjectNode e) {
    return results.receive(resultParser.parse(e.get("sourceId").asText(), e.toString()));
  }

  int held() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Integer.class));
  }

  @Test
  @DisplayName("[SPEC-014 R-9 R-15] 실제 종료 결과는 상태와 슬롯을 원자 반영하고 다음 청크를 배정한다")
  void normal() throws Exception {
    plan(UUID.randomUUID());
    UUID execution = dispatch.dispatchOne().orElseThrow();
    assertThat(receive(result(execution, true))).isEqualTo("APPLIED");
    assertThat(held()).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM chunk_executions", String.class))
        .isEqualTo("SUCCEEDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chunks WHERE status='PROCESSED'", Integer.class))
        .isEqualTo(1);
    assertThat(dispatch.dispatchOne()).isPresent();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
  @DisplayName("[SPEC-014 R-9 R-10] 성공/실패 재전달과 새 이벤트의 같은 결과도 슬롯을 한 번 반환한다")
  void duplicates(boolean success) throws Exception {
    plan(UUID.randomUUID());
    UUID id = dispatch.dispatchOne().orElseThrow();
    var e = result(id, success);
    assertThat(receive(e)).isEqualTo("APPLIED");
    assertThat(receive(e)).isEqualTo("APPLIED");
    e.put("eventId", UUID.randomUUID().toString());
    assertThat(receive(e)).isEqualTo("DUPLICATE");
    assertThat(held()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-014 R-12] 같은 이벤트/실행의 변경 결과와 다른 요청 식별은 거부한다")
  void conflicts() throws Exception {
    plan(UUID.randomUUID());
    UUID id = dispatch.dispatchOne().orElseThrow();
    var e = result(id, false);
    receive(e);
    e.put("errorCode", "OTHER_FAILURE");
    assertThatThrownBy(() -> receive(e)).isInstanceOf(IllegalStateException.class);
    e.put("eventId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> receive(e)).isInstanceOf(IllegalStateException.class);
    e.put("ownerId", UUID.randomUUID().toString());
    assertThatThrownBy(() -> receive(e)).isInstanceOf(IllegalArgumentException.class);
    assertThat(held()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  @DisplayName("[SPEC-014 R-11] 이전 시도의 늦은 결과는 새 실행 상태와 슬롯을 유지한다")
  void stale(boolean previouslyFinished) throws Exception {
    plan(UUID.randomUUID());
    UUID old = dispatch.dispatchOne().orElseThrow();
    var late = result(old, true);
    jdbc.update("UPDATE chunk_executions SET start_before=now() WHERE execution_id=?", old);
    if (previouslyFinished) {
      receive(late);
      late.put("eventId", UUID.randomUUID().toString());
    } else recovery.confirmStopped(old, UUID.randomUUID(), "verified-stop:old");
    // 재시도 배정 API를 추가하지 않는다. 과거/새 시도 동시 존재 상태만 fixture로 구성한다.
    jdbc.update(
        "UPDATE chunks SET status='PENDING' WHERE id=(SELECT chunk_id FROM chunk_executions WHERE execution_id=?)",
        old);
    UUID current = dispatch.dispatchOne().orElseThrow();
    assertThat(receive(late)).isEqualTo("IGNORED");
    assertThat(held()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM chunk_executions WHERE execution_id=?", String.class, current))
        .isEqualTo("DISPATCHED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chunks WHERE status='PROCESSED'", Integer.class))
        .isZero();
  }

  @Test
  @DisplayName("[SPEC-014 R-10] 동시 결과 수신은 종료/receipt/슬롯을 한 번만 반영한다")
  void concurrent() throws Exception {
    plan(UUID.randomUUID());
    var e = result(dispatch.dispatchOne().orElseThrow(), true);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
      var futures = new ArrayList<java.util.concurrent.Future<String>>();
      for (int i = 0; i < 16; i++) futures.add(pool.submit(() -> receive(e)));
      for (var f : futures)
        assertThat(f.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("APPLIED");
    }
    assertThat(held()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2, 3, 4})
  @DisplayName("[SPEC-014 R-13] CLI/결과 경쟁에서 먼저 확정한 종료만 상태와 슬롯을 바꾼다")
  void cliRace(int iteration) throws Exception {
    plan(UUID.randomUUID());
    UUID id = dispatch.dispatchOne().orElseThrow();
    var e = result(id, true);
    jdbc.update("UPDATE chunk_executions SET start_before=now() WHERE execution_id=?", id);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var cli =
          pool.submit(() -> recovery.confirmStopped(id, UUID.randomUUID(), "verified-stop:race"));
      var result = pool.submit(() -> receive(e));
      cli.get(20, java.util.concurrent.TimeUnit.SECONDS);
      assertThat(result.get(20, java.util.concurrent.TimeUnit.SECONDS)).isIn("APPLIED", "IGNORED");
    }
    assertThat(held()).isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM chunk_executions", String.class))
        .isIn("SUCCEEDED", "STOPPED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM execution_slots WHERE released_at IS NOT NULL",
                Integer.class))
        .isEqualTo(1);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "chunk_executions,status<>'SUCCEEDED'",
    "chunks,status<>'PROCESSED'",
    "execution_slots,released_at IS NULL",
    "execution_result_receipts,false"
  })
  @DisplayName("[SPEC-014 R-14] 각 결과 저장 실패는 receipt/상태/슬롯 전체를 롤백한다")
  void rollback(String table, String check) throws Exception {
    plan(UUID.randomUUID());
    var e = result(dispatch.dispatchOne().orElseThrow(), true);
    jdbc.execute(
        "ALTER TABLE " + table + " ADD CONSTRAINT result_failure CHECK(" + check + ") NOT VALID");
    try {
      assertThatThrownBy(() -> receive(e))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThat(held()).isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT status FROM chunk_executions", String.class))
          .isEqualTo("DISPATCHED");
      assertThat(
              jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM chunks WHERE status='DISPATCHED'", Integer.class))
          .isEqualTo(1);
    } finally {
      jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT result_failure");
    }
    assertThat(receive(e)).isEqualTo("APPLIED");
    assertThat(held()).isZero();
  }

  @Test
  @DisplayName("[SPEC-014 R-7] startBefore 경과만으로는 결과나 슬롯 반환을 만들지 않는다")
  void timeoutIsNotTermination() throws Exception {
    plan(UUID.randomUUID());
    UUID id = dispatch.dispatchOne().orElseThrow();
    jdbc.update("UPDATE chunk_executions SET start_before=now() WHERE execution_id=?", id);
    recovery.markExpired();
    assertThat(held()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isZero();
    assertThat(receive(result(id, true))).isEqualTo("APPLIED");
    assertThat(held()).isZero();
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"eventId", "sourceId", "requestId", "resultKey", "producedAt"})
  @DisplayName("[SPEC-014 R-9 R-12] 잘못된 식별·결과 참조·발행 시각은 상태와 슬롯을 변경하지 않는다")
  void malformed(String field) throws Exception {
    plan(UUID.randomUUID());
    var event = result(dispatch.dispatchOne().orElseThrow(), true);
    if (field.equals("sourceId") || field.equals("requestId"))
      event.put(field, UUID.randomUUID().toString());
    else event.put(field, "invalid");
    assertThatThrownBy(() -> receive(event)).isInstanceOf(IllegalArgumentException.class);
    assertThat(held()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM execution_result_receipts", Integer.class))
        .isZero();
  }
}
