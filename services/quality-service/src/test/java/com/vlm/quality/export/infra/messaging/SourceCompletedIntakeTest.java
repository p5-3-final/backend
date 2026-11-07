package com.vlm.quality.export.infra.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.support.Events;
import com.vlm.quality.support.IntegrationTest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@IntegrationTest
class SourceCompletedIntakeTest {

  private static final Duration WAIT = Duration.ofSeconds(30);

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[SPEC-030 R-13] source.completed를 받으면 영상별로 결과 파일 경로·jobId·받은 UTC 날짜를 기록한다")
  void recordsResultKey() {
    UUID source = UUID.randomUUID();
    UUID job = UUID.randomUUID();

    send(Events.sourceCompleted(source, job, 1));

    Map<String, Object> row = awaitRow(source, job);
    assertThat(row.get("result_key")).isEqualTo(Events.resultKey(source, job));
    assertThat(row.get("received_on").toString())
        .isEqualTo(LocalDate.now(ZoneOffset.UTC).toString());
  }

  @Test
  @DisplayName("[SPEC-030 R-13] 나중에 받은 job의 결과로 바꾸고, 같은 job의 낮은 revision은 버린다")
  void keepsLatestResult() {
    UUID source = UUID.randomUUID();
    UUID firstJob = UUID.randomUUID();
    UUID secondJob = UUID.randomUUID();
    send(Events.sourceCompleted(source, firstJob, 1));
    awaitRow(source, firstJob);

    send(Events.sourceCompleted(source, secondJob, 2));
    awaitRow(source, secondJob);
    send(Events.sourceCompleted(source, secondJob, 1));
    UUID marker = UUID.randomUUID();
    send(Events.sourceCompleted(marker, UUID.randomUUID(), 1));
    await().atMost(WAIT).until(() -> count(marker) == 1);

    Map<String, Object> row =
        jdbc.queryForMap("SELECT job_id, revision FROM source_results WHERE source_id = ?", source);
    assertThat(row).containsEntry("job_id", secondJob).containsEntry("revision", 2);
  }

  @Test
  @DisplayName("[SPEC-030 R-13] 삭제된 영상의 source.completed는 무시하고, video.deleted를 받으면 그 영상의 기록을 지운다")
  void dropsDeletedSources() {
    UUID kept = UUID.randomUUID();
    send(Events.sourceCompleted(kept, UUID.randomUUID(), 1));
    await().atMost(WAIT).until(() -> count(kept) == 1);

    kafka.send("video.deleted", kept.toString(), Events.videoDeleted(kept).toString()).join();
    await().atMost(WAIT).until(() -> count(kept) == 0);
    send(Events.sourceCompleted(kept, UUID.randomUUID(), 1));
    UUID marker = UUID.randomUUID();
    send(Events.sourceCompleted(marker, UUID.randomUUID(), 1));
    await().atMost(WAIT).until(() -> count(marker) == 1);

    assertThat(count(kept)).isZero();
  }

  private void send(ObjectNode event) {
    kafka.send("source.completed", event.get("sourceId").asText(), event.toString()).join();
  }

  private Map<String, Object> awaitRow(UUID source, UUID job) {
    await()
        .atMost(WAIT)
        .until(
            () ->
                jdbc.queryForObject(
                        "SELECT count(*) FROM source_results WHERE source_id = ? AND job_id = ?",
                        Integer.class,
                        source,
                        job)
                    == 1);
    return jdbc.queryForMap("SELECT * FROM source_results WHERE source_id = ?", source);
  }

  private int count(UUID source) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM source_results WHERE source_id = ?", Integer.class, source);
    return count == null ? 0 : count;
  }
}
