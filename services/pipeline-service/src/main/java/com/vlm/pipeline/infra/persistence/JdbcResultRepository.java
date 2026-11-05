package com.vlm.pipeline.infra.persistence;

import com.vlm.pipeline.domain.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcResultRepository implements ResultRepository {
  private final JdbcTemplate jdbc;

  @Override
  public String apply(ExecutionFinished e) {
    var receipts =
        jdbc.queryForList(
            "SELECT decision,payload=CAST(? AS jsonb) AS matches FROM execution_result_receipts WHERE event_id=?",
            e.payload(),
            e.eventId());
    if (!receipts.isEmpty()) {
      var r = receipts.getFirst();
      if (!Boolean.TRUE.equals(r.get("matches")))
        throw new IllegalStateException("Result event input conflicts");
      return Objects.requireNonNull((String) r.get("decision"));
    }
    var rows =
        jdbc.queryForList(
            """
      SELECT e.id,e.status,e.chunk_id,e.result_payload=CAST(? AS jsonb) AS same_result,
       c.chunk_id AS public_chunk,j.plan_id,j.source_id,p.normalization_id,n.owner_id,
       EXISTS(SELECT 1 FROM chunk_executions newer WHERE newer.chunk_id=e.chunk_id AND newer.id>e.id) AS superseded
      FROM chunk_executions e JOIN chunks c ON c.id=e.chunk_id JOIN jobs j ON j.id=c.job_id
      JOIN normalized_job_plans p ON p.job_plan_id=j.plan_id
      JOIN normalization_results n ON n.normalization_id=p.normalization_id
      WHERE e.execution_id=? FOR UPDATE OF e,c
      """,
            e.semantic(),
            e.executionId());
    if (rows.isEmpty()) throw new IllegalArgumentException("Unknown execution result");
    var r = rows.getFirst();
    if (!Objects.equals(r.get("public_chunk"), e.chunkId())
        || !Objects.equals(r.get("plan_id"), e.jobId())
        || !Objects.equals(r.get("source_id"), e.sourceId())
        || !Objects.equals(r.get("normalization_id"), e.normalizationId())
        || !Objects.equals(r.get("owner_id"), e.ownerId()))
      throw new IllegalArgumentException("Result execution identity mismatch");
    String status = Objects.requireNonNull((String) r.get("status"));
    String decision;
    if (status.equals("STOPPED") || Boolean.TRUE.equals(r.get("superseded"))) decision = "IGNORED";
    else if (status.equals("SUCCEEDED") || status.equals("FAILED")) {
      if (!Boolean.TRUE.equals(r.get("same_result")))
        throw new IllegalStateException("Execution terminal result conflicts");
      decision = "DUPLICATE";
    } else {
      if (!status.equals("DISPATCHED") && !status.equals("RECOVERY_REQUIRED"))
        throw new IllegalStateException("Unexpected execution state");
      jdbc.update(
          "UPDATE chunk_executions SET status=?,result_payload=CAST(? AS jsonb),updated_at=now() WHERE execution_id=?",
          e.outcome(),
          e.semantic(),
          e.executionId());
      if (jdbc.update(
              "UPDATE chunks SET status=?,updated_at=now() WHERE id=? AND status='DISPATCHED'",
              e.outcome().equals("SUCCEEDED") ? "PROCESSED" : "FAILED",
              r.get("chunk_id"))
          != 1) throw new IllegalStateException("Result chunk is not current dispatched chunk");
      if (jdbc.update(
              "UPDATE execution_slots SET released_at=now(),updated_at=now() WHERE execution_id=? AND released_at IS NULL",
              e.executionId())
          != 1) throw new IllegalStateException("Result execution has no held slot");
      decision = "APPLIED";
    }
    jdbc.update(
        "INSERT INTO execution_result_receipts(event_id,execution_id,payload,decision) VALUES(?,?,CAST(? AS jsonb),?)",
        e.eventId(),
        e.executionId(),
        e.payload(),
        decision);
    return decision;
  }
}
