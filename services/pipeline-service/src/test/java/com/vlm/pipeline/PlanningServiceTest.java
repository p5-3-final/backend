package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.vlm.common.error.BusinessException;
import com.vlm.pipeline.application.PlanningService;
import com.vlm.pipeline.domain.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Sql(statements = "TRUNCATE chunks, jobs RESTART IDENTITY CASCADE")
class PlanningServiceTest {
  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

  @Autowired PlanningService service;
  @Autowired JdbcTemplate jdbc;

  private PlanInput input() {
    return PlanInput.local(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("125.123456789"));
  }

  @Test
  @DisplayName("[SPEC-004 R-8 R-9 R-11] 트랜잭션 밖에서 입력과 순서 및 소수 구간을 복원한다")
  void persists() {
    var i = input();
    var created = service.create(i);
    var loaded = service.find(i.planId()).orElseThrow();
    assertThat(loaded).isEqualTo(created);
    assertThat(loaded.input()).isEqualTo(i);
    assertThat(loaded.chunks()).extracting(ChunkPlan::ordinal).containsExactly(0, 1, 2);
    assertThat(loaded.chunks().get(2).ownedEnd()).isEqualByComparingTo(i.duration());
    assertThat(service.find(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("[SPEC-004 R-12] 같은 요청은 중복 없이 기존 계획을 반환한다")
  void retries() {
    var i = input();
    var first = service.create(i);
    assertThat(service.create(i)).isEqualTo(first);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(strings = {"source", "duration", "profile", "chunk", "overlap"})
  @DisplayName("[SPEC-004 R-13] 동일 식별자의 각 입력 필드 충돌을 거절한다")
  void rejectsChangedInput(String field) {
    var i = input();
    var first = service.create(i);
    var changed =
        new PlanInput(
            i.planId(),
            field.equals("source") ? UUID.randomUUID() : i.sourceId(),
            field.equals("duration") ? BigDecimal.ONE : i.duration(),
            field.equals("profile") ? "custom" : i.profile(),
            field.equals("chunk") ? BigDecimal.ONE : i.chunkLength(),
            field.equals("overlap") ? BigDecimal.ZERO : i.overlap());
    assertThatThrownBy(() -> service.create(changed))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PlanningError.INPUT_CONFLICT));
    assertThat(service.find(i.planId()).orElseThrow()).isEqualTo(first);
  }

  @ParameterizedTest
  @ValueSource(strings = {"PLANNED", "RUNNING", "ANALYZED"})
  @DisplayName("[SPEC-004 R-14] 모든 활성 상태에서 다른 계획 생성을 거절한다")
  void active(String state) {
    var i = input();
    service.create(i);
    jdbc.update("UPDATE jobs SET status=?", state);
    var other = PlanInput.local(UUID.randomUUID(), i.sourceId(), i.duration());
    assertThatThrownBy(() -> service.create(other))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PlanningError.ACTIVE_JOB_EXISTS));
    assertThat(service.find(other.planId())).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"COMPLETED", "COMPLETED_WITH_ERRORS", "TIMED_OUT", "CANCELLED"})
  @DisplayName("[SPEC-004 R-12 R-15] 종료 후 새 계획을 허용하고 이전 계획 재시도로 재활성화하지 않는다")
  void terminal(String state) {
    var i = input();
    service.create(i);
    jdbc.update("UPDATE jobs SET status=?,updated_at=CURRENT_TIMESTAMP", state);
    var next = PlanInput.local(UUID.randomUUID(), i.sourceId(), i.duration());
    service.create(next);
    assertThat(service.create(i).status()).isEqualTo(state);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-004 R-10] 두 번째 청크 저장이 실패하면 Job과 앞선 Chunk도 롤백한다")
  void rollback() {
    jdbc.execute("ALTER TABLE chunks ADD CONSTRAINT test_fail_second CHECK (ordinal <> 1)");
    var i = input();
    try {
      assertThatThrownBy(() -> service.create(i))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isZero();
    } finally {
      jdbc.execute("ALTER TABLE chunks DROP CONSTRAINT test_fail_second");
    }
    assertThat(service.create(i).chunks()).hasSize(3);
  }

  @Test
  @DisplayName("[SPEC-004 R-16] 동시 재시도는 같은 계획 하나를 반환한다")
  void concurrentSame() throws Exception {
    var i = input();
    var results = race(i, i);
    assertThat(results).allSatisfy(r -> assertThat(r).isInstanceOf(JobPlan.class));
    assertThat(results.get(0)).isEqualTo(results.get(1));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-004 R-17] 다른 식별자의 동시 요청도 영상당 활성 계획 하나만 저장한다")
  void concurrentDifferent() throws Exception {
    var i = input();
    var other = PlanInput.local(UUID.randomUUID(), i.sourceId(), i.duration());
    var results = race(i, other);
    assertThat(results.stream().filter(JobPlan.class::isInstance).count()).isEqualTo(1);
    assertThat(results.stream().filter(PlanningError.ACTIVE_JOB_EXISTS::equals).count())
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-004 R-12] 소수 자릿수 표기만 다르면 같은 입력으로 재사용한다")
  void equivalentDecimals() {
    var i = PlanInput.local(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("120.0"));
    var first = service.create(i);
    var other =
        new PlanInput(
            i.planId(),
            i.sourceId(),
            new BigDecimal("120.00"),
            "local",
            new BigDecimal("60.000"),
            new BigDecimal("5.0"));
    assertThat(service.create(other)).isEqualTo(first);
  }

  @Test
  @DisplayName("[SPEC-004 R-13 R-16] 같은 식별자의 다른 입력이 동시에 오면 하나만 저장한다")
  void concurrentConflict() throws Exception {
    var i = input();
    var other = PlanInput.local(i.planId(), UUID.randomUUID(), i.duration());
    var results = race(i, other);
    assertThat(results.stream().filter(JobPlan.class::isInstance).count()).isEqualTo(1);
    assertThat(results.stream().filter(PlanningError.INPUT_CONFLICT::equals).count()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
  }

  @Test
  @DisplayName("[SPEC-004 R-17] 저장소 잠금을 우회해도 DB의 활성 Job 유일 제약이 보호한다")
  void uniqueActiveJob() {
    var i = input();
    service.create(i);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO jobs(plan_id,source_id,duration,profile,chunk_length,overlap,status) VALUES (?, ?, 1, 'local', 60, 5, 'PLANNED')",
                    UUID.randomUUID(),
                    i.sourceId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM jobs", Integer.class)).isEqualTo(1);
  }

  private List<Object> race(PlanInput a, PlanInput b) throws Exception {
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var futures =
          List.of(a, b).stream()
              .map(
                  i ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("Start timed out");
                            try {
                              return (Object) service.create(i);
                            } catch (BusinessException e) {
                              return (Object) e.getErrorCode();
                            }
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    }
  }
}
