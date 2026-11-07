package com.vlm.quality.evaluation.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.evaluation.domain.EvaluationRun;
import com.vlm.quality.evaluation.domain.EvaluationRunRepository;
import com.vlm.quality.evaluation.domain.GateFailure;
import com.vlm.quality.evaluation.domain.GateResult;
import com.vlm.quality.evaluation.domain.Metric;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcEvaluationRunRepository implements EvaluationRunRepository {

  private static final String COLUMNS =
      "run_id, golden_set_id, golden_set_version, kind, versions, metrics, started_at,"
          + " finished_at, trigger, commit_sha, gate_baseline_run_id, gate_passed, gate_tolerance,"
          + " gate_failures, received_at";
  private static final String FILTER =
      " WHERE (CAST(:setId AS UUID) IS NULL OR golden_set_id = :setId)";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public boolean insertIfAbsent(EvaluationRun run) {
    GateResult gate = run.gate();
    return jdbc.update(
            "INSERT INTO evaluation_runs (run_id, golden_set_id, golden_set_version, kind,"
                + " versions, metrics, started_at, finished_at, trigger, commit_sha,"
                + " gate_baseline_run_id, gate_passed, gate_tolerance, gate_failures, received_at,"
                + " created_at, updated_at) VALUES (:runId, :setId, :version, :kind,"
                + " CAST(:versions AS JSONB), CAST(:metrics AS JSONB), :startedAt, :finishedAt,"
                + " :trigger, :commitSha, :baseline, :passed, :tolerance,"
                + " CAST(:failures AS JSONB), :receivedAt, :receivedAt, :receivedAt)"
                + " ON CONFLICT (run_id) DO NOTHING",
            new MapSqlParameterSource()
                .addValue("runId", run.runId())
                .addValue("setId", run.goldenSetId())
                .addValue("version", run.goldenSetVersion())
                .addValue("kind", run.kind())
                .addValue("versions", write(run.versions()))
                .addValue("metrics", write(run.metrics()))
                .addValue("startedAt", Timestamp.from(run.startedAt()))
                .addValue("finishedAt", Timestamp.from(run.finishedAt()))
                .addValue("trigger", run.trigger())
                .addValue("commitSha", run.commitSha())
                .addValue("baseline", gate.baselineRunId())
                .addValue("passed", gate.passed())
                .addValue("tolerance", gate.tolerance())
                .addValue("failures", write(gate.failures()))
                .addValue("receivedAt", Timestamp.from(run.receivedAt())))
        == 1;
  }

  @Override
  public Optional<EvaluationRun> findByRunId(UUID runId) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + " FROM evaluation_runs WHERE run_id = :runId",
            new MapSqlParameterSource("runId", runId),
            mapper())
        .stream()
        .findFirst();
  }

  @Override
  public List<EvaluationRun> list(@Nullable UUID goldenSetId, int page, int size) {
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM evaluation_runs"
            + FILTER
            + " ORDER BY received_at DESC, id DESC LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource()
            .addValue("setId", goldenSetId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size),
        mapper());
  }

  @Override
  public long count(@Nullable UUID goldenSetId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM evaluation_runs" + FILTER,
            new MapSqlParameterSource("setId", goldenSetId),
            Long.class);
    return count == null ? 0 : count;
  }

  private RowMapper<EvaluationRun> mapper() {
    return (rs, row) ->
        new EvaluationRun(
            rs.getObject("run_id", UUID.class),
            rs.getObject("golden_set_id", UUID.class),
            rs.getInt("golden_set_version"),
            rs.getString("kind"),
            read(
                rs.getString("versions"),
                new TypeReference<LinkedHashMap<String, @Nullable String>>() {}),
            read(rs.getString("metrics"), new TypeReference<List<Metric>>() {}),
            rs.getTimestamp("started_at").toInstant(),
            rs.getTimestamp("finished_at").toInstant(),
            rs.getString("trigger"),
            rs.getString("commit_sha"),
            new GateResult(
                rs.getObject("gate_baseline_run_id", UUID.class),
                rs.getObject("gate_passed", Boolean.class),
                BigDecimal.valueOf(rs.getDouble("gate_tolerance")),
                read(rs.getString("gate_failures"), new TypeReference<List<GateFailure>>() {})),
            rs.getTimestamp("received_at").toInstant());
  }

  private String write(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write JSON", e);
    }
  }

  private <T> T read(String value, TypeReference<T> type) {
    try {
      return json.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot read JSON column", e);
    }
  }
}
