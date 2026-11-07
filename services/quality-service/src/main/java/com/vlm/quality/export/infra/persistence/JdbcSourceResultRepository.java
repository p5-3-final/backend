package com.vlm.quality.export.infra.persistence;

import com.vlm.quality.export.domain.SourceResult;
import com.vlm.quality.export.domain.SourceResultRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcSourceResultRepository implements SourceResultRepository {

  private static final String COLUMNS = "source_id, job_id, revision, result_key, received_at";

  // 같은 job의 늦은 재발행은 revision이 더 클 때만 반영한다(계약 source.completed revision).
  private static final String UPSERT =
      "INSERT INTO source_results (source_id, job_id, revision, result_key, received_at,"
          + " received_on) VALUES (:sourceId, :jobId, :revision, :resultKey, :receivedAt,"
          + " :receivedOn) ON CONFLICT (source_id) DO UPDATE SET job_id = EXCLUDED.job_id,"
          + " revision = EXCLUDED.revision, result_key = EXCLUDED.result_key,"
          + " received_at = EXCLUDED.received_at, received_on = EXCLUDED.received_on,"
          + " updated_at = now() WHERE source_results.job_id <> EXCLUDED.job_id"
          + " OR source_results.revision < EXCLUDED.revision";

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public boolean upsert(SourceResult result) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("sourceId", result.sourceId())
            .addValue("jobId", result.jobId())
            .addValue("revision", result.revision())
            .addValue("resultKey", result.resultKey())
            .addValue("receivedAt", Timestamp.from(result.receivedAt()))
            .addValue("receivedOn", result.receivedOn());
    return jdbc.update(UPSERT, params) == 1;
  }

  @Override
  public List<SourceResult> findBySources(List<UUID> sourceIds) {
    if (sourceIds.isEmpty()) {
      return List.of();
    }
    return jdbc.query(
        "SELECT " + COLUMNS + " FROM source_results WHERE source_id IN (:ids) ORDER BY source_id",
        new MapSqlParameterSource("ids", sourceIds),
        JdbcSourceResultRepository::map);
  }

  @Override
  public List<SourceResult> findReceivedBetween(LocalDate from, LocalDate to) {
    return jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM source_results WHERE received_on BETWEEN :from AND :to ORDER BY source_id",
        new MapSqlParameterSource().addValue("from", from).addValue("to", to),
        JdbcSourceResultRepository::map);
  }

  @Override
  public int deleteBySource(UUID sourceId) {
    return jdbc.update(
        "DELETE FROM source_results WHERE source_id = :sourceId",
        new MapSqlParameterSource("sourceId", sourceId));
  }

  private static SourceResult map(ResultSet rs, int row) throws SQLException {
    return new SourceResult(
        rs.getObject("source_id", UUID.class),
        rs.getObject("job_id", UUID.class),
        rs.getInt("revision"),
        rs.getString("result_key"),
        rs.getTimestamp("received_at").toInstant());
  }
}
