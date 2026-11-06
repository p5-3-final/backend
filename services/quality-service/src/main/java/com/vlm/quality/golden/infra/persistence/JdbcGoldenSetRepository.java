package com.vlm.quality.golden.infra.persistence;

import com.vlm.quality.golden.domain.GoldenSet;
import com.vlm.quality.golden.domain.GoldenSetRepository;
import java.sql.Timestamp;
import java.time.Instant;
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
class JdbcGoldenSetRepository implements GoldenSetRepository {

  // 초안 수는 목록마다 하위 질의로 센다. 목록 한 쪽(최대 100개)마다 색인으로 세므로 N+1 질의가 없다.
  private static final String SELECT =
      "SELECT g.id, g.set_id, g.name, g.split, g.origin, g.description, g.latest_version,"
          + " g.baseline_run_id, g.created_at,"
          + " (SELECT count(*) FROM golden_draft_entries d WHERE d.golden_set_id = g.id) AS drafts"
          + " FROM golden_sets g";

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public boolean insertIfNameFree(
      UUID setId, String name, String split, @Nullable String description, Instant now) {
    return jdbc.update(
            "INSERT INTO golden_sets (set_id, name, split, origin, description, created_at,"
                + " updated_at) VALUES (:setId, :name, :split, 'review', :description, :now, :now)"
                + " ON CONFLICT (name) DO NOTHING",
            new MapSqlParameterSource()
                .addValue("setId", setId)
                .addValue("name", name)
                .addValue("split", split)
                .addValue("description", description)
                .addValue("now", Timestamp.from(now)))
        == 1;
  }

  @Override
  public Optional<GoldenSet> findBySetId(UUID setId) {
    return jdbc
        .query(
            SELECT + " WHERE g.set_id = :setId", new MapSqlParameterSource("setId", setId), MAPPER)
        .stream()
        .findFirst();
  }

  @Override
  public List<GoldenSet> list(int page, int size) {
    return jdbc.query(
        SELECT + " ORDER BY g.name LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource().addValue("limit", size).addValue("offset", (long) page * size),
        MAPPER);
  }

  @Override
  public long count() {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM golden_sets", new MapSqlParameterSource(), Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public void setBaseline(UUID setId, UUID runId, Instant now) {
    jdbc.update(
        "UPDATE golden_sets SET baseline_run_id = :runId, updated_at = :now WHERE set_id = :setId",
        new MapSqlParameterSource()
            .addValue("setId", setId)
            .addValue("runId", runId)
            .addValue("now", Timestamp.from(now)));
  }

  @Override
  public int startFreeze(UUID setId, Instant now, Instant staleBefore) {
    return jdbc.update(
        "UPDATE golden_sets SET freeze_started_at = :now, updated_at = :now WHERE set_id = :setId"
            + " AND (freeze_started_at IS NULL OR freeze_started_at < :staleBefore)",
        new MapSqlParameterSource()
            .addValue("setId", setId)
            .addValue("now", Timestamp.from(now))
            .addValue("staleBefore", Timestamp.from(staleBefore)));
  }

  @Override
  public void clearFreeze(UUID setId, Instant now) {
    jdbc.update(
        "UPDATE golden_sets SET freeze_started_at = NULL, updated_at = :now WHERE set_id = :setId",
        new MapSqlParameterSource().addValue("setId", setId).addValue("now", Timestamp.from(now)));
  }

  @Override
  public void finishFreeze(UUID setId, int version, Instant now) {
    jdbc.update(
        "UPDATE golden_sets SET latest_version = :version, freeze_started_at = NULL,"
            + " updated_at = :now WHERE set_id = :setId",
        new MapSqlParameterSource()
            .addValue("setId", setId)
            .addValue("version", version)
            .addValue("now", Timestamp.from(now)));
  }

  private static final RowMapper<GoldenSet> MAPPER =
      (rs, row) ->
          new GoldenSet(
              rs.getLong("id"),
              rs.getObject("set_id", UUID.class),
              rs.getString("name"),
              rs.getString("split"),
              rs.getString("origin"),
              rs.getString("description"),
              rs.getObject("latest_version", Integer.class),
              rs.getObject("baseline_run_id", UUID.class),
              rs.getInt("drafts"),
              rs.getTimestamp("created_at").toInstant());
}
