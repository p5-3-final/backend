package com.vlm.quality.stage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DailyStageAggregationTest {

  @Autowired DailyStageAggregationJob job;
  @Autowired StageTimingIngestService ingest;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  @DisplayName("[SPEC-029 R-23] 하루(UTC) 원본을 stageId별, stageId·버전 키·값별로 집계하고 다시 돌려도 같은 행이다")
  void aggregatesDayIdempotently() {
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    var at = day.atTime(10, 0).toInstant(ZoneOffset.UTC);
    UUID source = UUID.randomUUID();
    for (int i = 1; i <= 4; i++) {
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.timing(
                  source,
                  "analyzer.judge",
                  at,
                  i * 100.0,
                  i == 4 ? "error" : "ok",
                  Map.of("modelId", i <= 2 ? "a" : "b"))));
    }
    // 다른 날 기록은 들어가지 않는다.
    ingest.ingest(
        StageFixtures.batch(
            StageFixtures.timing(source, "analyzer.judge", at.plusSeconds(86_400), 9_999, "ok")));

    job.aggregate(day);
    job.aggregate(day);

    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT version_key, version_value, record_count, p50_ms, p95_ms, error_count"
                + " FROM stage_daily_stats WHERE stat_date = ? AND stage_id = 'analyzer.judge'"
                + " ORDER BY version_key NULLS FIRST, version_value",
            day);
    assertThat(rows).hasSize(3);
    assertThat(rows.get(0))
        .containsEntry("version_key", null)
        .containsEntry("record_count", 4L)
        .containsEntry("error_count", 1L);
    // percentile_cont 보간은 부동소수점이라 CPU(FMA 사용 여부)에 따라 마지막 자리가 다르다.
    assertThat((Double) rows.get(0).get("p50_ms")).isCloseTo(250.0, within(1e-6));
    assertThat((Double) rows.get(0).get("p95_ms")).isCloseTo(385.0, within(1e-6));
    assertThat(rows.get(1)).containsEntry("version_value", "a").containsEntry("p50_ms", 150.0);
    assertThat(rows.get(2)).containsEntry("version_value", "b").containsEntry("error_count", 1L);
  }

  @Test
  @DisplayName("[SPEC-029 R-23] 다른 Pod가 잠금을 잡고 있으면 건너뛴다")
  void skipsWhenLocked() throws Exception {
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    ingest.ingest(
        StageFixtures.batch(
            StageFixtures.timing(
                UUID.randomUUID(),
                "analyzer.judge",
                day.atTime(1, 0).toInstant(ZoneOffset.UTC),
                100,
                "ok")));

    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(false);
      try (PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
        lock.setLong(1, DailyStageAggregationJob.LOCK_KEY);
        lock.execute();
      }
      assertThat(job.aggregate(day)).isFalse();
      other.rollback();
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM stage_daily_stats", Integer.class))
        .isZero();
  }
}
