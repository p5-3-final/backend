package com.vlm.pipeline.infra.persistence;

import com.vlm.pipeline.domain.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcDispatchRepository implements DispatchRepository {
  private final JdbcTemplate jdbc;

  @Override
  public void lock() {
    // SPEC-010 R-2~4: 세 단계 상한·공정 순서·회수를 같은 DB에서 직렬화한다.
    jdbc.queryForList("SELECT pg_advisory_xact_lock(5,0)");
  }

  @Override
  public void ensureConfiguration(
      int userLimit, int sourceLimit, int globalLimit, long startWindowMs) {
    var rows = jdbc.queryForList("SELECT * FROM dispatch_states WHERE id=1");
    if (rows.isEmpty()) {
      jdbc.update(
          "INSERT INTO dispatch_states(id,user_limit,source_limit,global_limit,start_window_ms) OVERRIDING SYSTEM VALUE VALUES(1,?,?,?,?)",
          userLimit,
          sourceLimit,
          globalLimit,
          startWindowMs);
    } else {
      var r = rows.getFirst();
      boolean same =
          Objects.equals(r.get("user_limit"), userLimit)
              && Objects.equals(r.get("source_limit"), sourceLimit)
              && Objects.equals(r.get("global_limit"), globalLimit)
              && Objects.equals(r.get("start_window_ms"), startWindowMs);
      if (!same) {
        if (heldSlots() != 0)
          throw new IllegalStateException("Dispatch configuration conflicts with held slots");
        jdbc.update(
            "UPDATE dispatch_states SET user_limit=?,source_limit=?,global_limit=?,start_window_ms=?,updated_at=now() WHERE id=1",
            userLimit,
            sourceLimit,
            globalLimit,
            startWindowMs);
      }
    }
  }

  private long heldSlots() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM execution_slots WHERE released_at IS NULL", Long.class));
  }

  @Override
  public Instant now() {
    return Objects.requireNonNull(jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class))
        .toInstant();
  }

  @Override
  public Optional<DispatchCandidate> next(int userLimit, int sourceLimit, int globalLimit) {
    if (heldSlots() >= globalLimit) return Optional.empty();
    return jdbc
        .query(
            """
        SELECT c.*, j.plan_id, j.source_id, n.owner_id, n.normalization_id,
               n.metadata->>'normalizedKey' AS normalized_key, a.version,
               a.definition->>'mode' AS mode, a.definition->>'inferenceProfileVersion' AS inference_profile,
               (SELECT r.trace_parent FROM normalized_receipts r WHERE r.job_plan_id=j.plan_id
                AND r.trace_parent IS NOT NULL ORDER BY r.id LIMIT 1) AS trace_parent
        FROM chunks c JOIN jobs j ON j.id=c.job_id
        JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id
        JOIN normalization_results n ON n.normalization_id=p.normalization_id AND n.result='READY'
        JOIN analysis_settings a ON a.version=p.settings_version
        LEFT JOIN dispatch_users u ON u.owner_id=n.owner_id
        WHERE c.status='PENDING' AND j.status IN ('PLANNED','RUNNING')
          AND (SELECT count(*) FROM execution_slots s WHERE s.owner_id=n.owner_id AND s.released_at IS NULL) < ?
          AND (SELECT count(*) FROM execution_slots s WHERE s.source_id=j.source_id AND s.released_at IS NULL) < ?
        ORDER BY COALESCE(u.last_sequence,0), n.owner_id, j.created_at, j.id, c.ordinal
        LIMIT 1 FOR UPDATE OF c,j
        """,
            (rs, n) ->
                new DispatchCandidate(
                    rs.getLong("id"),
                    UUID.fromString(rs.getString("chunk_id")),
                    UUID.fromString(rs.getString("plan_id")),
                    UUID.fromString(rs.getString("source_id")),
                    UUID.fromString(rs.getString("owner_id")),
                    UUID.fromString(rs.getString("normalization_id")),
                    Objects.requireNonNull(rs.getString("normalized_key")),
                    ms(rs.getBigDecimal("owned_start")),
                    ms(rs.getBigDecimal("owned_end")),
                    ms(rs.getBigDecimal("expanded_start")),
                    ms(rs.getBigDecimal("expanded_end")),
                    new AnalysisSettings(
                        Objects.requireNonNull(rs.getString("version")),
                        Objects.requireNonNull(rs.getString("mode")),
                        Objects.requireNonNull(rs.getString("inference_profile"))),
                    rs.getString("trace_parent")),
            userLimit,
            sourceLimit)
        .stream()
        .findFirst();
  }

  private static long ms(@Nullable BigDecimal seconds) {
    return Objects.requireNonNull(seconds).movePointRight(3).longValueExact();
  }

  @Override
  public void createExecution(
      DispatchCandidate c, UUID executionId, UUID eventId, Instant startBefore) {
    jdbc.update(
        "INSERT INTO chunk_executions(execution_id,chunk_id,event_id,status,start_before) VALUES(?,?,?,'DISPATCHED',?)",
        executionId,
        c.id(),
        eventId,
        Timestamp.from(startBefore));
  }

  @Override
  public void occupy(DispatchCandidate c, UUID executionId) {
    jdbc.update(
        "INSERT INTO execution_slots(execution_id,owner_id,source_id) VALUES(?,?,?)",
        executionId,
        c.ownerId(),
        c.sourceId());
  }

  @Override
  public void markDispatched(DispatchCandidate c) {
    if (jdbc.update(
            "UPDATE chunks SET status='DISPATCHED',updated_at=now() WHERE id=? AND status='PENDING'",
            c.id())
        != 1) throw new IllegalStateException("Chunk is no longer pending");
    jdbc.update(
        "UPDATE jobs SET status='RUNNING',updated_at=now() WHERE plan_id=? AND status='PLANNED'",
        c.jobId());
  }

  @Override
  public void advanceFairness(UUID ownerId) {
    Long sequence =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "UPDATE dispatch_states SET sequence=sequence+1,updated_at=now() WHERE id=1 RETURNING sequence",
                Long.class));
    jdbc.update(
        "INSERT INTO dispatch_users(owner_id,last_sequence) VALUES(?,?) ON CONFLICT(owner_id) DO UPDATE SET last_sequence=EXCLUDED.last_sequence,updated_at=now()",
        ownerId,
        sequence);
  }

  @Override
  public void preserveTrace(UUID eventId, @Nullable String traceparent) {
    jdbc.update("UPDATE outbox_events SET trace_parent=? WHERE event_id=?", traceparent, eventId);
  }

  @Override
  public int expire() {
    return jdbc.update(
        "UPDATE chunk_executions SET status='RECOVERY_REQUIRED',updated_at=now() WHERE status='DISPATCHED' AND start_before<=clock_timestamp()");
  }

  @Override
  public void confirmStopped(UUID executionId, UUID confirmationId, String evidence) {
    var rows =
        jdbc.queryForList(
            "SELECT * FROM chunk_executions WHERE execution_id=? FOR UPDATE", executionId);
    if (rows.isEmpty()) throw new IllegalArgumentException("Unknown execution");
    var r = rows.getFirst();
    if (Objects.equals(r.get("status"), "SUCCEEDED") || Objects.equals(r.get("status"), "FAILED"))
      return;
    if (Objects.equals(r.get("status"), "STOPPED")) {
      if (!Objects.equals(r.get("confirmation_id"), confirmationId)
          || !Objects.equals(r.get("evidence"), evidence))
        throw new IllegalStateException("Stop confirmation conflicts with recorded evidence");
      return;
    }
    if (!Objects.equals(r.get("status"), "RECOVERY_REQUIRED"))
      throw new IllegalStateException("Execution has not expired");
    jdbc.update(
        "UPDATE chunk_executions SET status='STOPPED',confirmation_id=?,evidence=?,updated_at=now() WHERE execution_id=?",
        confirmationId,
        evidence,
        executionId);
    if (jdbc.update(
            "UPDATE execution_slots SET released_at=now(),updated_at=now() WHERE execution_id=? AND released_at IS NULL",
            executionId)
        != 1) throw new IllegalStateException("Execution has no held slot");
    if (jdbc.update(
            "UPDATE chunks SET status='FAILED',updated_at=now() WHERE id=? AND status='DISPATCHED'",
            r.get("chunk_id"))
        != 1) throw new IllegalStateException("Chunk is no longer dispatched");
  }

  @Override
  public long recoveryCount() {
    return Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT count(*) FROM chunk_executions e JOIN execution_slots s USING(execution_id) WHERE s.released_at IS NULL AND (e.status='RECOVERY_REQUIRED' OR e.start_before<=clock_timestamp())",
            Long.class));
  }
}
