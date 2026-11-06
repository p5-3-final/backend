package com.vlm.quality.stage.infra.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.stage.domain.StageStat;
import com.vlm.quality.stage.domain.StageTiming;
import com.vlm.quality.stage.domain.StageTimingRepository;
import com.vlm.quality.stage.domain.TrendDay;
import com.vlm.quality.stage.domain.TrendQuery;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * 측정 원본(월 단위 범위 파티션)과 일 집계.
 *
 * <p>백분위는 앱이 아니라 PostgreSQL {@code percentile_cont}(선형 보간)로 계산한다. 영상 하나에 수천 건(NFR-4)을 앱으로 옮기지 않고,
 * 영상별 리포트·일 집계가 같은 정의를 쓴다. 음수 {@code durationMs}는 개수에만 넣는다(R-22).
 */
@Repository
@RequiredArgsConstructor
class JdbcStageTimingRepository implements StageTimingRepository {

  // 마이그레이션(V202611071100)과 같은 이름 규칙. DDL에 넣기 전에 이 형식인지 확인한다.
  private static final Pattern PARTITION = Pattern.compile("stage_timings_y(\\d{4})m(\\d{2})");
  private static final String STATS =
      "count(*) AS record_count,"
          + " COALESCE(sum(duration_ms) FILTER (WHERE duration_ms >= 0), 0) AS total_ms,"
          + " COALESCE(percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms)"
          + " FILTER (WHERE duration_ms >= 0), 0) AS p50_ms,"
          + " COALESCE(percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)"
          + " FILTER (WHERE duration_ms >= 0), 0) AS p95_ms,"
          + " count(*) FILTER (WHERE status = 'error') AS error_count";

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper json;

  @Override
  public void replace(List<StageTiming> timings) {
    if (timings.isEmpty()) {
      return;
    }
    // 같은 timingId는 같은 원본 메시지(같은 키 → 같은 파티션)에서만 나와 한 소비자가 차례로 처리하므로 경합이 없다.
    jdbc.update(
        "DELETE FROM stage_timings WHERE timing_id IN (:ids)",
        new MapSqlParameterSource(
            "ids", timings.stream().map(StageTiming::timingId).distinct().toList()));
    jdbc.batchUpdate(
        "INSERT INTO stage_timings (timing_id, stage_id, path, source_id, candidate_id, service,"
            + " attempt, started_at, ended_at, duration_ms, status, versions) VALUES (:timingId,"
            + " :stageId, :path, :sourceId, :candidateId, :service, :attempt, :startedAt,"
            + " :endedAt, :durationMs, :status, CAST(:versions AS JSONB))",
        timings.stream().map(this::params).toArray(SqlParameterSource[]::new));
  }

  @Override
  public List<StageStat> sourceReport(UUID sourceId, Instant since) {
    return jdbc.query(
        "SELECT path, stage_id, "
            + STATS
            + " FROM stage_timings WHERE source_id = :sourceId AND started_at >= :since"
            + " GROUP BY path, stage_id ORDER BY path, stage_id",
        new MapSqlParameterSource()
            .addValue("sourceId", sourceId)
            .addValue("since", Timestamp.from(since)),
        (rs, row) ->
            new StageStat(
                rs.getString("path"),
                rs.getString("stage_id"),
                rs.getLong("record_count"),
                rs.getDouble("total_ms"),
                rs.getDouble("p50_ms"),
                rs.getDouble("p95_ms"),
                rs.getLong("error_count")));
  }

  @Override
  public int aggregateDay(LocalDate day) {
    var params =
        new MapSqlParameterSource()
            .addValue("day", Date.valueOf(day))
            .addValue("from", Timestamp.from(day.atStartOfDay().toInstant(ZoneOffset.UTC)))
            .addValue(
                "to", Timestamp.from(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)));
    String upsert =
        " ON CONFLICT ON CONSTRAINT uq_stage_daily_stats DO UPDATE SET"
            + " record_count = EXCLUDED.record_count, p50_ms = EXCLUDED.p50_ms,"
            + " p95_ms = EXCLUDED.p95_ms, error_count = EXCLUDED.error_count, updated_at = now()";
    int all =
        jdbc.update(
            "INSERT INTO stage_daily_stats (stat_date, stage_id, version_key, version_value,"
                + " record_count, p50_ms, p95_ms, error_count)"
                + " SELECT :day, stage_id, NULL, NULL, record_count, p50_ms, p95_ms, error_count"
                + " FROM (SELECT stage_id, "
                + STATS
                + " FROM stage_timings WHERE started_at >= :from AND started_at < :to"
                + " GROUP BY stage_id) s"
                + upsert,
            params);
    int byVersion =
        jdbc.update(
            "INSERT INTO stage_daily_stats (stat_date, stage_id, version_key, version_value,"
                + " record_count, p50_ms, p95_ms, error_count)"
                + " SELECT :day, stage_id, version_key, version_value, record_count, p50_ms,"
                + " p95_ms, error_count FROM (SELECT stage_id, v.key AS version_key,"
                + " v.value AS version_value, "
                + STATS
                + " FROM stage_timings CROSS JOIN LATERAL jsonb_each_text(versions) AS v(key, value)"
                + " WHERE started_at >= :from AND started_at < :to"
                + " GROUP BY stage_id, v.key, v.value) s"
                + upsert,
            params);
    return all + byVersion;
  }

  @Override
  public List<TrendDay> trend(TrendQuery query) {
    return jdbc.query(
        "SELECT stat_date, record_count, p50_ms, p95_ms, error_count FROM stage_daily_stats"
            + " WHERE stage_id = :stageId AND stat_date BETWEEN :from AND :to"
            + " AND version_key IS NOT DISTINCT FROM CAST(:key AS VARCHAR)"
            + " AND version_value IS NOT DISTINCT FROM CAST(:value AS VARCHAR)"
            + " ORDER BY stat_date",
        new MapSqlParameterSource()
            .addValue("stageId", query.stageId())
            .addValue("from", Date.valueOf(query.from()))
            .addValue("to", Date.valueOf(query.to()))
            .addValue("key", query.versionKey())
            .addValue("value", query.versionValue()),
        (rs, row) ->
            new TrendDay(
                rs.getDate("stat_date").toLocalDate(),
                rs.getLong("record_count"),
                rs.getDouble("p50_ms"),
                rs.getDouble("p95_ms"),
                rs.getLong("error_count")));
  }

  @Override
  public void createPartition(YearMonth month) {
    Instant lower = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    Instant upper = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    // DDL은 파라미터를 받지 않는다. 이름·경계는 YearMonth에서 만든 값만 넣는다.
    jdbc.getJdbcTemplate()
        .execute(
            "CREATE TABLE IF NOT EXISTS "
                + name(month)
                + " PARTITION OF stage_timings FOR VALUES FROM ('"
                + lower
                + "') TO ('"
                + upper
                + "')");
  }

  @Override
  public List<String> dropPartitionsEndingBefore(Instant cutoff) {
    List<String> names =
        jdbc.getJdbcTemplate()
            .queryForList(
                "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid"
                    + " JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'stage_timings'",
                String.class);
    List<String> dropped = new ArrayList<>();
    for (String partition : names) {
      Matcher matcher = PARTITION.matcher(partition);
      if (!matcher.matches()) {
        continue;
      }
      YearMonth month =
          YearMonth.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
      Instant upper = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
      if (!upper.isAfter(cutoff)) {
        jdbc.getJdbcTemplate().execute("DROP TABLE IF EXISTS " + name(month));
        dropped.add(partition);
      }
    }
    return List.copyOf(dropped);
  }

  private static String name(YearMonth month) {
    return String.format("stage_timings_y%04dm%02d", month.getYear(), month.getMonthValue());
  }

  private MapSqlParameterSource params(StageTiming timing) {
    try {
      return new MapSqlParameterSource()
          .addValue("timingId", timing.timingId())
          .addValue("stageId", timing.stageId())
          .addValue("path", timing.path())
          .addValue("sourceId", timing.sourceId())
          .addValue("candidateId", timing.candidateId())
          .addValue("service", timing.service())
          .addValue("attempt", timing.attempt())
          .addValue("startedAt", Timestamp.from(timing.startedAt()))
          .addValue("endedAt", Timestamp.from(timing.endedAt()))
          .addValue("durationMs", timing.durationMs())
          .addValue("status", timing.status())
          .addValue("versions", json.writeValueAsString(timing.versions()));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("cannot write versions", e);
    }
  }
}
