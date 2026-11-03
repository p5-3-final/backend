package com.vlm.pipeline.infra.persistence;

import com.vlm.pipeline.domain.*;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcPlanRepository implements PlanRepository {
  private final JdbcTemplate jdbc;

  @Override
  public void lockPlan(UUID id) {
    lock(1, id);
  }

  @Override
  public void lockSource(UUID id) {
    lock(2, id);
  }

  // 계획 ID와 source ID는 별도 namespace다. 해시 충돌이 나도 직렬화 범위만 넓어지고 안전성은 유지된다.
  private void lock(int namespace, UUID id) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(?, hashtext(?))", namespace, id.toString());
  }

  @Override
  public Optional<JobPlan> find(UUID planId) {
    var jobs =
        jdbc.query(
            "SELECT * FROM jobs WHERE plan_id = ?",
            (rs, n) ->
                new JobPlan(
                    new PlanInput(
                        planId,
                        UUID.fromString(rs.getString("source_id")),
                        Objects.requireNonNull(rs.getBigDecimal("duration")),
                        Objects.requireNonNull(rs.getString("profile")),
                        Objects.requireNonNull(rs.getBigDecimal("chunk_length")),
                        Objects.requireNonNull(rs.getBigDecimal("overlap"))),
                    Objects.requireNonNull(rs.getString("status")),
                    jdbc.query(
                        "SELECT c.* FROM chunks c JOIN jobs j ON c.job_id=j.id WHERE j.plan_id=? ORDER BY c.ordinal",
                        (cs, m) ->
                            new ChunkPlan(
                                cs.getInt("ordinal"),
                                Objects.requireNonNull(cs.getBigDecimal("owned_start")),
                                Objects.requireNonNull(cs.getBigDecimal("owned_end")),
                                Objects.requireNonNull(cs.getBigDecimal("expanded_start")),
                                Objects.requireNonNull(cs.getBigDecimal("expanded_end"))),
                        planId)),
            planId);
    return jobs.stream().findFirst();
  }

  @Override
  public boolean hasActiveJob(UUID sourceId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM jobs WHERE source_id=? AND status IN ('PLANNED','RUNNING','ANALYZED'))",
            Boolean.class,
            sourceId));
  }

  @Override
  public void save(JobPlan plan) {
    var i = plan.input();
    Long jobId =
        Objects.requireNonNull(
            jdbc.queryForObject(
                "INSERT INTO jobs(plan_id,source_id,duration,profile,chunk_length,overlap,status) VALUES (?,?,?,?,?,?,?) RETURNING id",
                Long.class,
                i.planId(),
                i.sourceId(),
                i.duration(),
                i.profile(),
                i.chunkLength(),
                i.overlap(),
                plan.status()));
    jdbc.batchUpdate(
        "INSERT INTO chunks(job_id,ordinal,owned_start,owned_end,expanded_start,expanded_end) VALUES (?,?,?,?,?,?)",
        plan.chunks().stream()
            .map(
                c ->
                    new Object[] {
                      jobId,
                      c.ordinal(),
                      c.ownedStart(),
                      c.ownedEnd(),
                      c.expandedStart(),
                      c.expandedEnd()
                    })
            .toList());
  }
}
