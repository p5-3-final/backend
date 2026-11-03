package com.vlm.pipeline.infra.persistence;

import com.vlm.common.error.BusinessException;
import com.vlm.pipeline.domain.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcNormalizedReceiptRepository implements NormalizedReceiptRepository {
  private final JdbcTemplate jdbc;

  @Override
  public void lockEvent(UUID id) {
    lock(3, id);
  }

  @Override
  public void lockNormalization(UUID id) {
    lock(4, id);
  }

  private void lock(int namespace, UUID id) {
    jdbc.queryForList("SELECT pg_advisory_xact_lock(?,hashtext(?))", namespace, id.toString());
  }

  @Override
  public Optional<IntakeResult> findReceipt(NormalizedVideo event) {
    return jdbc
        .query(
            "SELECT job_plan_id,result,payload=CAST(? AS jsonb) AS matches FROM normalized_receipts WHERE event_id=?",
            (rs, n) -> {
              if (!rs.getBoolean("matches"))
                throw new BusinessException(IntakeError.EVENT_CONFLICT);
              return new IntakeResult(
                  event.eventId(),
                  rs.getObject("job_plan_id", UUID.class),
                  Objects.requireNonNull(rs.getString("result")));
            },
            event.payload(),
            event.eventId())
        .stream()
        .findFirst();
  }

  @Override
  public void ensureNormalization(NormalizedVideo event) {
    var existing =
        jdbc.queryForList(
            "SELECT metadata=CAST(? AS jsonb) FROM normalization_results WHERE normalization_id=?",
            Boolean.class,
            event.metadata(),
            event.normalizationId());
    if (!existing.isEmpty()) {
      if (!Boolean.TRUE.equals(existing.getFirst()))
        throw new BusinessException(IntakeError.NORMALIZATION_CONFLICT);
      return;
    }
    jdbc.update(
        "INSERT INTO normalization_results(normalization_id,source_id,owner_id,normalization_version,result,duration_ms,metadata) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))",
        event.normalizationId(),
        event.sourceId(),
        event.ownerId(),
        event.version(),
        event.ready() ? "READY" : "REJECTED",
        event.ready() ? event.durationMs() : null,
        event.metadata());
  }

  @Override
  public void ensureSettings(AnalysisSettings settings) {
    jdbc.update(
        "INSERT INTO analysis_settings(version,definition) VALUES (?,CAST(? AS jsonb)) ON CONFLICT (version) DO NOTHING",
        settings.version(),
        settings.definition());
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT definition=CAST(? AS jsonb) FROM analysis_settings WHERE version=?",
            Boolean.class,
            settings.definition(),
            settings.version()))) throw new BusinessException(IntakeError.SETTINGS_CONFLICT);
  }

  @Override
  public Optional<UUID> findJob(UUID id, String version) {
    return jdbc
        .queryForList(
            "SELECT job_plan_id FROM normalized_job_plans WHERE normalization_id=? AND settings_version=?",
            UUID.class,
            id,
            version)
        .stream()
        .findFirst();
  }

  @Override
  public void linkJob(UUID id, String version, UUID job) {
    jdbc.update(
        "INSERT INTO normalized_job_plans(normalization_id,settings_version,job_plan_id) VALUES (?,?,?)",
        id,
        version,
        job);
  }

  @Override
  public void saveReceipt(NormalizedVideo event, @Nullable UUID job, @Nullable String traceparent) {
    jdbc.update(
        "INSERT INTO normalized_receipts(event_id,normalization_id,job_plan_id,result,payload,trace_parent) VALUES (?,?,?,?,CAST(? AS jsonb),?)",
        event.eventId(),
        event.normalizationId(),
        job,
        event.ready() ? "READY" : "REJECTED",
        event.payload(),
        traceparent);
  }
}
