package com.vlm.pipeline.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.vlm.pipeline.domain.JobExecutionRepository;
import java.sql.Timestamp;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcJobExecutionRepository implements JobExecutionRepository {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;

  @Override
  public Optional<ObjectNode> refresh(UUID jobId) {
    var rows =
        jdbc.queryForList(
            """
   SELECT j.id,j.status,j.source_id,n.owner_id,j.execution_sequence AS sequence,s.snapshot::text
   FROM jobs j JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id
   JOIN normalization_results n ON n.normalization_id=p.normalization_id
   LEFT JOIN job_execution_summaries s ON s.job_id=j.id WHERE j.plan_id=? FOR UPDATE OF j
   """,
            jobId);
    if (rows.isEmpty()) return Optional.empty();
    var j = rows.getFirst();
    // SPEC-015: 실제 종료된 허용 실패만 재시도한다. STOPPED·불명 실행은 대상이 아니다.
    jdbc.update(
        """
   UPDATE chunks c SET status='RETRY_PENDING',retry_at=e.updated_at + j.retry_delay_ms * interval '1 millisecond',updated_at=now()
   FROM jobs j,chunk_executions e
   WHERE c.job_id=j.id AND j.plan_id=? AND c.status='FAILED' AND e.chunk_id=c.id AND e.status='FAILED'
    AND e.result_payload->>'errorCode'=ANY(j.retryable_error_codes)
    AND NOT EXISTS(SELECT 1 FROM chunk_executions newer WHERE newer.chunk_id=c.id AND newer.id>e.id)
    AND (SELECT count(*) FROM chunk_executions a WHERE a.chunk_id=c.id)<j.max_attempts
   """,
        jobId);
    ObjectNode snapshot = mapper.createObjectNode();
    snapshot.put("jobId", jobId.toString());
    snapshot.put("sourceId", Objects.requireNonNull(j.get("source_id")).toString());
    snapshot.put("ownerId", Objects.requireNonNull(j.get("owner_id")).toString());
    snapshot.put("status", Objects.requireNonNull(j.get("status")).toString());
    ObjectNode counts = snapshot.putObject("execution");
    for (String k :
        List.of("total", "succeeded", "failed", "pending", "retryPending", "running", "unknown"))
      counts.put(k, 0);
    var issues = snapshot.putArray("issues");
    var failures = snapshot.putArray("failures");
    var chunks =
        jdbc.queryForList(
            """
   SELECT c.chunk_id,c.status,c.retry_at,e.execution_id,e.status AS execution_status,
    e.result_payload->>'errorCode' AS error_code,
    e.start_before<=clock_timestamp() AS expired,
    (SELECT count(*) FROM chunk_executions a WHERE a.chunk_id=c.id) AS attempt
   FROM chunks c LEFT JOIN LATERAL(SELECT * FROM chunk_executions a WHERE a.chunk_id=c.id ORDER BY a.id DESC LIMIT 1)e ON true
   WHERE c.job_id=? ORDER BY c.ordinal
   """,
            j.get("id"));
    for (var c : chunks) {
      increment(counts, "total");
      String raw = Objects.requireNonNull(c.get("status")).toString();
      String state =
          switch (raw) {
            case "PROCESSED" -> "succeeded";
            case "FAILED" -> "failed";
            case "PENDING" -> "pending";
            case "RETRY_PENDING" -> "retryPending";
            default ->
                Objects.equals(c.get("execution_status"), "RECOVERY_REQUIRED")
                        || Boolean.TRUE.equals(c.get("expired"))
                    ? "unknown"
                    : "running";
          };
      increment(counts, state);
      if (List.of("failed", "retryPending", "unknown").contains(state)) {
        var issue = issues.addObject();
        issue.put("chunkId", Objects.requireNonNull(c.get("chunk_id")).toString());
        issue.put("executionId", Objects.toString(c.get("execution_id"), null));
        issue.put("state", state);
        issue.put("attempt", ((Number) Objects.requireNonNull(c.get("attempt"))).intValue());
        String error =
            state.equals("unknown")
                ? "START_DEADLINE_ELAPSED"
                : Objects.equals(c.get("execution_status"), "STOPPED")
                    ? "OPERATOR_CONFIRMED_STOP"
                    : Objects.toString(c.get("error_code"), null);
        issue.put("errorCode", error);
        var retryAt = (Timestamp) c.get("retry_at");
        issue.put("retryAt", retryAt == null ? null : retryAt.toInstant().toString());
      }
    }
    for (var f :
        jdbc.queryForList(
            """
   SELECT c.chunk_id,e.execution_id,e.status,e.result_payload->>'errorCode' AS error_code,
    row_number() OVER(PARTITION BY c.id ORDER BY e.id) AS attempt
   FROM chunks c JOIN chunk_executions e ON e.chunk_id=c.id WHERE c.job_id=? ORDER BY c.ordinal,e.id
   """,
            j.get("id"))) {
      if (!List.of("FAILED", "STOPPED")
          .contains(Objects.requireNonNull(f.get("status")).toString())) continue;
      var failure = failures.addObject();
      for (String key : List.of("chunk_id", "execution_id"))
        failure.put(
            key.equals("chunk_id") ? "chunkId" : "executionId",
            Objects.requireNonNull(f.get(key)).toString());
      failure.put("attempt", ((Number) Objects.requireNonNull(f.get("attempt"))).intValue());
      failure.put("status", Objects.requireNonNull(f.get("status")).toString());
      failure.put(
          "errorCode",
          Objects.equals(f.get("status"), "STOPPED")
              ? "OPERATOR_CONFIRMED_STOP"
              : Objects.toString(f.get("error_code"), "PROCESSING_FAILED"));
    }
    int total = counts.path("total").asInt(),
        success = counts.path("succeeded").asInt(),
        failed = counts.path("failed").asInt();
    boolean terminal = total > 0 && success + failed == total;
    String state =
        terminal
            ? (failed == 0 ? "SUCCEEDED" : success == 0 ? "FAILED" : "PARTIAL_FAILED")
            : counts.path("unknown").asInt() > 0
                ? "UNKNOWN"
                : counts.path("pending").asInt() == total ? "PENDING" : "RUNNING";
    counts.put("status", state);
    counts.put("terminal", terminal);
    counts.put("progressPercent", total == 0 ? 0 : (int) ((long) (success + failed) * 100 / total));
    counts.put("successPercent", total == 0 ? 0 : (int) ((long) success * 100 / total));
    long sequence =
        j.get("sequence") == null
            ? 0
            : ((Number) Objects.requireNonNull(j.get("sequence"))).longValue();
    if (j.get("snapshot") != null) {
      var prior = parse(Objects.requireNonNull(j.get("snapshot")).toString());
      prior.remove("sequence");
      if (prior.equals(snapshot)) {
        jdbc.update(
            "UPDATE job_execution_summaries SET checked_at=clock_timestamp() WHERE job_id=?",
            j.get("id"));
        return Optional.empty();
      }
    }
    snapshot.put("sequence", sequence + 1);
    jdbc.update(
        "UPDATE jobs SET execution_sequence=?,updated_at=now() WHERE id=?",
        sequence + 1,
        j.get("id"));
    jdbc.update(
        """
   INSERT INTO job_execution_summaries(job_id,sequence,snapshot) VALUES(?,?,CAST(? AS jsonb))
   ON CONFLICT(job_id) DO UPDATE SET sequence=EXCLUDED.sequence,snapshot=EXCLUDED.snapshot,checked_at=clock_timestamp(),updated_at=now()
   """,
        j.get("id"),
        sequence + 1,
        snapshot.toString());
    return Optional.of(snapshot);
  }

  private static void increment(ObjectNode n, String k) {
    n.put(k, n.path(k).asInt() + 1);
  }

  private ObjectNode parse(String s) {
    try {
      return (ObjectNode) mapper.readTree(s);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Invalid stored snapshot", e);
    }
  }

  @Override
  public Optional<ObjectNode> find(UUID jobId, UUID owner) {
    return jdbc
        .query(
            """
   SELECT s.snapshot::text FROM job_execution_summaries s JOIN jobs j ON j.id=s.job_id
   JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id JOIN normalization_results n ON n.normalization_id=p.normalization_id
   WHERE j.plan_id=? AND n.owner_id=?
   """,
            (rs, n) -> parse(Objects.requireNonNull(rs.getString(1))),
            jobId,
            owner)
        .stream()
        .findFirst();
  }

  @Override
  public List<UUID> due(int batchSize) {
    return jdbc.query(
        """
  SELECT j.plan_id FROM jobs j JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id
  LEFT JOIN job_execution_summaries s ON s.job_id=j.id ORDER BY s.checked_at NULLS FIRST,j.id LIMIT ?
  """,
        (rs, n) -> UUID.fromString(rs.getString(1)),
        batchSize);
  }

  @Override
  public List<UUID> live() {
    return jdbc.query(
        """
  SELECT DISTINCT j.plan_id FROM jobs j JOIN chunks c ON c.job_id=j.id WHERE c.status='DISPATCHED'
  """,
        (rs, n) -> UUID.fromString(rs.getString(1)));
  }

  @Override
  public Optional<UUID> jobForExecution(UUID executionId) {
    return jdbc
        .query(
            "SELECT j.plan_id FROM jobs j JOIN chunks c ON c.job_id=j.id JOIN chunk_executions e ON e.chunk_id=c.id WHERE e.execution_id=?",
            (rs, n) -> UUID.fromString(rs.getString(1)),
            executionId)
        .stream()
        .findFirst();
  }

  @Override
  public boolean owned(UUID jobId, UUID ownerId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM jobs j JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id JOIN normalization_results n ON n.normalization_id=p.normalization_id WHERE j.plan_id=? AND n.owner_id=?)",
            Boolean.class,
            jobId,
            ownerId));
  }
}
