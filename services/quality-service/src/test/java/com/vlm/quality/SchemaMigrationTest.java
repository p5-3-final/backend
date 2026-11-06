package com.vlm.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.quality.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class SchemaMigrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-029] 마이그레이션이 검토·Golden Set·평가·측정·알림 테이블을 만든다")
  void createsTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class);

    assertThat(tables)
        .contains(
            "review_items",
            "deleted_sources",
            "golden_sets",
            "golden_draft_entries",
            "golden_set_versions",
            "evaluation_runs",
            "stage_timings",
            "stage_daily_stats",
            "alert_events");
  }

  @Test
  @DisplayName("[SPEC-029 R-25] 측정 원본은 월 단위 파티션이고 이번 달부터 4개월 치가 있다")
  void createsMonthlyPartitions() {
    List<String> partitions =
        jdbc.queryForList(
            "SELECT c.relname FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid"
                + " JOIN pg_class p ON p.oid = i.inhparent WHERE p.relname = 'stage_timings'",
            String.class);

    assertThat(partitions)
        .hasSizeGreaterThanOrEqualTo(4)
        .allMatch(n -> n.matches("stage_timings_y\\d{4}m\\d{2}"));
  }

  @Test
  @DisplayName("[SPEC-029 R-6] 맡은 사람 없이 ASSIGNED인 검토 항목은 저장할 수 없다")
  void rejectsAssignedWithoutAssignee() {
    Integer constraints =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_constraint WHERE conname = 'ck_review_items_assignee'",
            Integer.class);

    assertThat(constraints).isEqualTo(1);
  }
}
