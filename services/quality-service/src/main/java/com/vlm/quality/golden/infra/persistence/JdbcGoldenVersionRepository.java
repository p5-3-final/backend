package com.vlm.quality.golden.infra.persistence;

import com.vlm.quality.golden.domain.GoldenSetVersion;
import com.vlm.quality.golden.domain.GoldenVersionRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcGoldenVersionRepository implements GoldenVersionRepository {

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public void insert(long goldenSetRowId, GoldenSetVersion version) {
    jdbc.update(
        "INSERT INTO golden_set_versions (golden_set_id, version, entry_count, manifest_key,"
            + " frozen_at, frozen_by, created_at, updated_at) VALUES (:set, :version, :count,"
            + " :key, :frozenAt, :frozenBy, :frozenAt, :frozenAt)",
        new MapSqlParameterSource()
            .addValue("set", goldenSetRowId)
            .addValue("version", version.version())
            .addValue("count", version.entryCount())
            .addValue("key", version.manifestKey())
            .addValue("frozenAt", Timestamp.from(version.frozenAt()))
            .addValue("frozenBy", version.frozenBy()));
  }

  @Override
  public List<GoldenSetVersion> list(long goldenSetRowId, UUID setId, int page, int size) {
    return jdbc.query(
        "SELECT version, entry_count, manifest_key, frozen_at, frozen_by FROM golden_set_versions"
            + " WHERE golden_set_id = :set ORDER BY version DESC LIMIT :limit OFFSET :offset",
        new MapSqlParameterSource()
            .addValue("set", goldenSetRowId)
            .addValue("limit", size)
            .addValue("offset", (long) page * size),
        (rs, row) ->
            new GoldenSetVersion(
                setId,
                rs.getInt("version"),
                rs.getInt("entry_count"),
                rs.getString("manifest_key"),
                rs.getTimestamp("frozen_at").toInstant(),
                rs.getObject("frozen_by", UUID.class)));
  }

  @Override
  public long count(long goldenSetRowId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM golden_set_versions WHERE golden_set_id = :set",
            new MapSqlParameterSource("set", goldenSetRowId),
            Long.class);
    return count == null ? 0 : count;
  }

  @Override
  public boolean exists(UUID setId, int version) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM golden_set_versions v JOIN golden_sets g"
                + " ON g.id = v.golden_set_id WHERE g.set_id = :setId AND v.version = :version)",
            new MapSqlParameterSource().addValue("setId", setId).addValue("version", version),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }
}
