package com.vlm.quality.stage.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.quality.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class StageTimingPartitionTest {

  @Autowired StageTimingPartitionJob job;
  @Autowired JdbcTemplate jdbc;

  // 같은 DB를 쓰는 다른 테스트가 이번 달 파티션에 적재하므로 지운 파티션을 되살린다.
  @AfterEach
  void restorePartitions() {
    job.manage(Instant.now());
  }

  @Test
  @DisplayName("[SPEC-029 R-25] 3개월 앞 파티션을 만들고, 상한이 90일 전 이하인 월 파티션을 지우며 일 집계는 남긴다")
  void managesMonthlyPartitions() {
    YearMonth now = YearMonth.now(ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO stage_daily_stats (stat_date, stage_id, record_count, p50_ms, p95_ms, error_count)"
            + " VALUES (?, 'analyzer.judge', 1, 1, 1, 0)",
        now.minusMonths(6).atDay(1));

    // 시계를 5개월 뒤로 옮기면 지금 달 파티션(상한 = 다음 달 1일)은 90일보다 오래됐다.
    Instant later = now.plusMonths(5).atDay(15).atStartOfDay().toInstant(ZoneOffset.UTC);
    job.manage(later);

    List<String> partitions = partitions();
    assertThat(partitions)
        .doesNotContain(name(now))
        .contains(name(now.plusMonths(5)), name(now.plusMonths(8)));
    assertThat(partitions).doesNotContain(name(now.plusMonths(9)));
    // 상한이 90일 전보다 뒤인 파티션은 남는다(마이그레이션이 만든 +2·+3개월).
    assertThat(partitions).doesNotContain(name(now.plusMonths(1)));
    assertThat(partitions).contains(name(now.plusMonths(2)), name(now.plusMonths(3)));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM stage_daily_stats", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-029 R-25] 다시 돌려도 같은 파티션 목록이다")
  void isIdempotent() {
    Instant now = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    job.manage(now);
    List<String> first = partitions();
    job.manage(now);

    assertThat(partitions()).containsExactlyInAnyOrderElementsOf(first);
  }

  private List<String> partitions() {
    return jdbc.queryForList(
        "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid"
            + " JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'stage_timings'",
        String.class);
  }

  private static String name(YearMonth month) {
    return String.format("stage_timings_y%04dm%02d", month.getYear(), month.getMonthValue());
  }
}
