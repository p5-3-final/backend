package com.vlm.quality.alert.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.alert.domain.AlertDay;
import com.vlm.quality.alert.domain.AlertEventRepository;
import com.vlm.quality.alert.domain.AlertRecord;
import com.vlm.quality.alert.domain.AlertReportQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 후보별 긴급 알림 기록(candidate_id 하나에 한 행).
 *
 * <p>같은 후보의 메시지는 같은 키(sourceId)로 와 한 소비자가 차례로 처리한다. 순서가 뒤집힐 수 있어 pending과 판정은 서로 다른 열만 채운다.
 */
@Repository
@RequiredArgsConstructor
class JdbcAlertEventRepository implements AlertEventRepository {

  // 측정 기록은 알림보다 조금 앞서거나 늦게 시작할 수 있어 리포트 기간 앞뒤로 하루씩 넓혀 읽는다.
  private static final Duration TIMING_MARGIN = Duration.ofDays(1);

  private static final String INSERT =
      "INSERT INTO alert_events (candidate_id, source_id, camera_id, event_type, severity,"
          + " pending_at, resolved_at, status, dismiss_reason, evidence_frame_keys)"
          + " VALUES (:candidateId, :sourceId, :cameraId, :eventType, :severity, :pendingAt,"
          + " :resolvedAt, :status, :dismissReason, CAST(:frames AS JSONB))";

  // 1차 알림 지연은 event-analyzer의 analyzer.alert 측정(alert.candidate 수신부터 첫 alert.raised 발행까지)으로 잰다.
  // quality-service는 alert.candidate를 소비하지 않는다. 재시도로 기록이 여럿이면 마지막에 끝난 것을 쓴다.
  private static final String REPORT =
      "WITH a AS (SELECT candidate_id, status, pending_at, resolved_at,"
          + " CAST(COALESCE(pending_at, resolved_at) AT TIME ZONE 'UTC' AS DATE) AS day"
          + " FROM alert_events WHERE COALESCE(pending_at, resolved_at) >= :from"
          + " AND COALESCE(pending_at, resolved_at) < :to"
          + " AND (CAST(:cameraId AS UUID) IS NULL OR camera_id = :cameraId)),"
          + " f AS (SELECT DISTINCT ON (candidate_id) candidate_id, duration_ms FROM stage_timings"
          + " WHERE stage_id = 'analyzer.alert' AND candidate_id IS NOT NULL AND duration_ms >= 0"
          + " AND started_at >= :timingsFrom AND started_at < :timingsTo"
          + " ORDER BY candidate_id, ended_at DESC),"
          + " j AS (SELECT a.day, a.status, f.duration_ms AS first_ms,"
          + " CAST(EXTRACT(EPOCH FROM (a.resolved_at - a.pending_at)) * 1000 AS DOUBLE PRECISION)"
          + " AS judge_ms FROM a LEFT JOIN f ON f.candidate_id = CAST(a.candidate_id AS VARCHAR))"
          + " SELECT day,"
          + " count(*) FILTER (WHERE status = 'pending') AS pending_count,"
          + " count(*) FILTER (WHERE status = 'confirmed') AS confirmed_count,"
          + " count(*) FILTER (WHERE status = 'dismissed') AS dismissed_count,"
          + " percentile_cont(0.5) WITHIN GROUP (ORDER BY first_ms) AS first_p50,"
          + " percentile_cont(0.95) WITHIN GROUP (ORDER BY first_ms) AS first_p95,"
          + " percentile_cont(0.5) WITHIN GROUP (ORDER BY judge_ms) FILTER (WHERE judge_ms >= 0)"
          + " AS judge_p50,"
          + " percentile_cont(0.95) WITHIN GROUP (ORDER BY judge_ms) FILTER (WHERE judge_ms >= 0)"
          + " AS judge_p95"
          + " FROM j GROUP BY day ORDER BY day";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public void recordPending(AlertRecord pending) {
    jdbc.update(
        INSERT
            + " ON CONFLICT (candidate_id) DO UPDATE SET pending_at = EXCLUDED.pending_at,"
            + " updated_at = now() WHERE alert_events.pending_at IS NULL",
        params(pending)
            .addValue("pendingAt", Timestamp.from(pending.occurredAt()))
            .addValue("resolvedAt", null));
  }

  @Override
  public boolean recordResolution(AlertRecord resolution) {
    return jdbc.update(
            INSERT
                + " ON CONFLICT (candidate_id) DO UPDATE SET status = EXCLUDED.status,"
                + " resolved_at = EXCLUDED.resolved_at, dismiss_reason = EXCLUDED.dismiss_reason,"
                + " severity = EXCLUDED.severity,"
                + " evidence_frame_keys = EXCLUDED.evidence_frame_keys, updated_at = now()"
                + " WHERE alert_events.status = 'pending'",
            params(resolution)
                .addValue("pendingAt", null)
                .addValue("resolvedAt", Timestamp.from(resolution.occurredAt())))
        == 1;
  }

  @Override
  public int deleteBySource(UUID sourceId) {
    return jdbc.update(
        "DELETE FROM alert_events WHERE source_id = :sourceId",
        new MapSqlParameterSource("sourceId", sourceId));
  }

  @Override
  public List<AlertDay> report(AlertReportQuery query) {
    Instant from = query.from().atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant to = query.to().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    return jdbc.query(
        REPORT,
        new MapSqlParameterSource()
            .addValue("from", Timestamp.from(from))
            .addValue("to", Timestamp.from(to))
            .addValue("timingsFrom", Timestamp.from(from.minus(TIMING_MARGIN)))
            .addValue("timingsTo", Timestamp.from(to.plus(TIMING_MARGIN)))
            .addValue("cameraId", query.cameraId()),
        (rs, row) ->
            new AlertDay(
                rs.getDate("day").toLocalDate(),
                rs.getLong("pending_count"),
                rs.getLong("confirmed_count"),
                rs.getLong("dismissed_count"),
                nullableDouble(rs, "first_p50"),
                nullableDouble(rs, "first_p95"),
                nullableDouble(rs, "judge_p50"),
                nullableDouble(rs, "judge_p95")));
  }

  private MapSqlParameterSource params(AlertRecord alert) {
    return new MapSqlParameterSource()
        .addValue("candidateId", alert.candidateId())
        .addValue("sourceId", alert.sourceId())
        .addValue("cameraId", alert.cameraId())
        .addValue("eventType", alert.eventType())
        .addValue("severity", alert.severity())
        .addValue("status", alert.status().wire())
        .addValue("dismissReason", alert.dismissReason())
        .addValue("frames", write(alert.evidenceFrameKeys()));
  }

  private static @Nullable Double nullableDouble(ResultSet rs, String column) throws SQLException {
    double value = rs.getDouble(column);
    return rs.wasNull() ? null : value;
  }

  private String write(List<String> value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write evidence frame keys", e);
    }
  }
}
