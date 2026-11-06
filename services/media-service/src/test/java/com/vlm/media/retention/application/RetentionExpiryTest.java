package com.vlm.media.retention.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.vlm.media.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class RetentionExpiryTest {

  @Autowired JdbcTemplate jdbc;
  @Autowired RetentionExpiryJob expiryJob;

  @Test
  @DisplayName("[SPEC-018 R-17] 보존 만료 시각이 지난 최소 기록을 지우고 철회 이벤트는 내지 않는다")
  void deletesExpiredRecords() {
    UUID expired = insert(Instant.now().minus(Duration.ofMinutes(1)));
    UUID active = insert(Instant.now().plus(Duration.ofDays(1)));

    int deleted = expiryJob.expire();

    assertThat(deleted).isEqualTo(1);
    assertThat(jdbc.queryForList("SELECT source_id FROM retained_videos", UUID.class))
        .containsExactly(active)
        .doesNotContain(expired);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class)).isZero();
  }

  private UUID insert(Instant retainUntil) {
    UUID sourceId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO retained_videos (source_id, owner_id, deleted_at, retain_until)"
            + " VALUES (?, ?, now(), ?)",
        sourceId,
        UUID.randomUUID(),
        Timestamp.from(retainUntil));
    return sourceId;
  }
}
