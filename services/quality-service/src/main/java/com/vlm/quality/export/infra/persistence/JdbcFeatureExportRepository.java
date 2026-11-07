package com.vlm.quality.export.infra.persistence;

import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.export.domain.ExportStatus;
import com.vlm.quality.export.domain.FeatureExport;
import com.vlm.quality.export.domain.FeatureExportRepository;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcFeatureExportRepository implements FeatureExportRepository {

  private static final String SELECT =
      "SELECT id, export_id, status, golden_set_id, from_date, to_date, file_key, row_count,"
          + " source_count, requested_by, requested_at, finished_at FROM feature_exports";

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public void insert(UUID exportId, ExportScope scope, UUID requestedBy, Instant now) {
    jdbc.update(
        "INSERT INTO feature_exports (export_id, status, golden_set_id, from_date, to_date,"
            + " requested_by, requested_at) VALUES (:exportId, 'PENDING', :goldenSetId, :from,"
            + " :to, :requestedBy, :now)",
        new MapSqlParameterSource()
            .addValue("exportId", exportId)
            .addValue("goldenSetId", scope.goldenSetId())
            .addValue("from", scope.from())
            .addValue("to", scope.to())
            .addValue("requestedBy", requestedBy)
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public Optional<FeatureExport> findByExportId(UUID exportId) {
    return first(SELECT + " WHERE export_id = :id", new MapSqlParameterSource("id", exportId));
  }

  @Override
  public Optional<FeatureExport> claim(Instant now, Instant staleBefore) {
    List<Long> claimed =
        jdbc.queryForList(
            "UPDATE feature_exports SET status = 'RUNNING', heartbeat_at = :now, updated_at = :now"
                + " WHERE id = (SELECT id FROM feature_exports WHERE status = 'PENDING' OR (status"
                + " = 'RUNNING' AND (heartbeat_at IS NULL OR heartbeat_at < :stale)) ORDER BY"
                + " requested_at, id LIMIT 1 FOR UPDATE SKIP LOCKED) RETURNING id",
            new MapSqlParameterSource()
                .addValue("now", Timestamp.from(now))
                .addValue("stale", Timestamp.from(staleBefore)),
            Long.class);
    if (claimed.isEmpty()) {
      return Optional.empty();
    }
    return first(SELECT + " WHERE id = :id", new MapSqlParameterSource("id", claimed.get(0)));
  }

  @Override
  public void heartbeat(long id, Instant now) {
    jdbc.update(
        "UPDATE feature_exports SET heartbeat_at = :now, updated_at = :now WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now)));
  }

  @Override
  public void complete(long id, String fileKey, int rowCount, int sourceCount, Instant now) {
    jdbc.update(
        "UPDATE feature_exports SET status = 'COMPLETED', file_key = :fileKey, row_count ="
            + " :rows, source_count = :sources, finished_at = :now, updated_at = :now WHERE id ="
            + " :id",
        new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("fileKey", fileKey)
            .addValue("rows", rowCount)
            .addValue("sources", sourceCount)
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public void fail(long id, Instant now) {
    jdbc.update(
        "UPDATE feature_exports SET status = 'FAILED', finished_at = :now, updated_at = :now"
            + " WHERE id = :id",
        new MapSqlParameterSource().addValue("id", id).addValue("now", Timestamp.from(now)));
  }

  private Optional<FeatureExport> first(String sql, MapSqlParameterSource params) {
    return jdbc.query(sql, params, JdbcFeatureExportRepository::map).stream().findFirst();
  }

  private static FeatureExport map(ResultSet rs, int row) throws SQLException {
    Timestamp finished = rs.getTimestamp("finished_at");
    return new FeatureExport(
        rs.getLong("id"),
        rs.getObject("export_id", UUID.class),
        ExportStatus.valueOf(rs.getString("status")),
        new ExportScope(
            rs.getObject("golden_set_id", UUID.class),
            date(rs.getDate("from_date")),
            date(rs.getDate("to_date"))),
        rs.getString("file_key"),
        rs.getInt("row_count"),
        rs.getInt("source_count"),
        rs.getObject("requested_by", UUID.class),
        rs.getTimestamp("requested_at").toInstant(),
        finished == null ? null : finished.toInstant());
  }

  private static @Nullable LocalDate date(@Nullable Date value) {
    return value == null ? null : value.toLocalDate();
  }
}
