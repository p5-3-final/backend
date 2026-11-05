package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.pipeline.api.NormalizedEventParser;
import com.vlm.pipeline.application.*;
import com.vlm.pipeline.domain.AnalysisSettings;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
      "pipeline.dispatch.enabled=true",
      "pipeline.dispatch.interval-ms=50",
      "pipeline.dispatch.start-window-ms=1000",
      "vlm.outbox.scheduling-enabled=true",
      "vlm.outbox.publish-interval-ms=50"
    })
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DispatchScheduleTest.Topics.class)
class DispatchScheduleTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Container @ServiceConnection
  static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.3.2");

  @TestConfiguration(proxyBeanMethods = false)
  static class Topics {
    @Bean
    org.apache.kafka.clients.admin.NewTopic requestTopic() {
      return new org.apache.kafka.clients.admin.NewTopic("chunk.requested", 1, (short) 1);
    }
  }

  @Autowired NormalizedIntakeService intake;
  @Autowired NormalizedEventParser parser;
  @Autowired DispatchRecoveryService recovery;
  @Autowired JdbcTemplate jdbc;

  int count(String sql) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class));
  }

  @Test
  @DisplayName("[SPEC-010 R-2 R-5 R-9 R-10 R-13] opt-in 주기가 배정·실제 발행하고 종료 확인 뒤 다음 청크를 배정한다")
  void schedulesAndPublishes() throws Exception {
    var e =
        (ObjectNode)
            new ObjectMapper()
                .readTree(
                    Objects.requireNonNull(
                        getClass()
                            .getResourceAsStream(
                                "/contracts/events/video.normalized.v2.schema.json")))
                .get("examples")
                .get(0)
                .deepCopy();
    intake.receive(
        parser.parse(e.get("sourceId").asText(), e.toString()),
        new AnalysisSettings("schedule-v1", "local", "infer-v1"),
        null);
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(count("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL"))
                  .isEqualTo(1);
              assertThat(count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
                  .isEqualTo(1);
            });
    UUID first =
        Objects.requireNonNull(
            jdbc.queryForObject("SELECT execution_id FROM chunk_executions", UUID.class));
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        count(
                            "SELECT count(*) FROM chunk_executions WHERE status='RECOVERY_REQUIRED'"))
                    .isEqualTo(1));
    assertThat(count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
        .isEqualTo(1);
    assertThat(count("SELECT count(*) FROM outbox_events")).isEqualTo(1);
    recovery.confirmStopped(first, UUID.randomUUID(), "verification:worker-never-started");
    await()
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(count("SELECT count(*) FROM outbox_events WHERE published_at IS NOT NULL"))
                  .isEqualTo(2);
              assertThat(count("SELECT count(*) FROM execution_slots WHERE released_at IS NULL"))
                  .isEqualTo(1);
            });
    assertThat(count("SELECT count(DISTINCT chunk_id) FROM chunk_executions")).isEqualTo(2);
    assertThat(count("SELECT count(*) FROM chunk_executions WHERE status='STOPPED'")).isEqualTo(1);
  }
}
