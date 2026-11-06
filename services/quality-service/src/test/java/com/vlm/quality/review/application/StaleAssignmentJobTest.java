package com.vlm.quality.review.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class StaleAssignmentJobTest {

  @Autowired StaleAssignmentJob job;
  @Autowired ReviewFixtures fixtures;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;

  @Test
  @DisplayName("[SPEC-029 R-10] 24시간 넘게 라벨 없이 ASSIGNED인 항목만 UNASSIGNED로 되돌린다")
  void releasesStaleAssignments() {
    UUID stale = assignedAt(Instant.now().minus(Duration.ofHours(25)));
    UUID fresh = assignedAt(Instant.now().minus(Duration.ofHours(1)));

    int released = job.releaseStale();

    assertThat(released).isEqualTo(1);
    assertThat(status(stale)).isEqualTo("UNASSIGNED");
    assertThat(status(fresh)).isEqualTo("ASSIGNED");
  }

  @Test
  @DisplayName("[SPEC-029 R-10] 다른 Pod가 잠금을 잡고 있으면 아무것도 하지 않는다")
  void skipsWhenAnotherPodHoldsLock() throws Exception {
    UUID stale = assignedAt(Instant.now().minus(Duration.ofHours(25)));

    try (Connection other = dataSource.getConnection()) {
      other.setAutoCommit(false);
      try (PreparedStatement lock = other.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
        lock.setLong(1, StaleAssignmentJob.LOCK_KEY);
        lock.execute();
      }
      assertThat(job.releaseStale()).isZero();
      other.rollback();
    }
    assertThat(status(stale)).isEqualTo("ASSIGNED");
  }

  private UUID assignedAt(Instant at) {
    UUID itemId = fixtures.reviewRequired(UUID.randomUUID());
    jdbc.update(
        "UPDATE review_items SET status = 'ASSIGNED', assignee_id = ?, assigned_at = ?"
            + " WHERE item_id = ?",
        UUID.randomUUID(),
        Timestamp.from(at),
        itemId);
    return itemId;
  }

  private String status(UUID itemId) {
    return jdbc.queryForObject(
        "SELECT status FROM review_items WHERE item_id = ?", String.class, itemId);
  }
}
