package com.vlm.quality.shared.infra.persistence;

import com.vlm.quality.shared.domain.DeletedSources;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JdbcDeletedSources implements DeletedSources {

  private final JdbcTemplate jdbc;

  @Override
  public void record(UUID sourceId, Instant deletedAt) {
    jdbc.update(
        "INSERT INTO deleted_sources (source_id, deleted_at) VALUES (?, ?)"
            + " ON CONFLICT (source_id) DO NOTHING",
        sourceId,
        Timestamp.from(deletedAt));
  }

  @Override
  public boolean isDeleted(UUID sourceId) {
    Boolean exists =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM deleted_sources WHERE source_id = ?)",
            Boolean.class,
            sourceId);
    return Boolean.TRUE.equals(exists);
  }
}
