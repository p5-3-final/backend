package com.vlm.pipeline.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChunkPlannerTest {
  @Test
  @DisplayName("[SPEC-004 R-3 R-4 R-5] 잔여 구간을 보존하고 영상 범위 안에서 겹침을 적용한다")
  void ranges() {
    var input =
        new PlanInput(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("250"),
            "custom",
            new BigDecimal("120"),
            new BigDecimal("5"));
    var chunks = ChunkPlanner.calculate(input);
    assertThat(chunks).hasSize(3);
    assertThat(chunks.get(0))
        .isEqualTo(
            new ChunkPlan(
                0, BigDecimal.ZERO, new BigDecimal("120"), BigDecimal.ZERO, new BigDecimal("125")));
    assertThat(chunks.get(1).ownedStart()).isEqualByComparingTo("120");
    assertThat(chunks.get(1).expandedStart()).isEqualByComparingTo("115");
    assertThat(chunks.get(1).expandedEnd()).isEqualByComparingTo("245");
    assertThat(chunks.get(2).ownedEnd()).isEqualByComparingTo("250");
    assertThat(chunks.get(2).expandedEnd()).isEqualByComparingTo("250");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "599.999,60",
    "600,120",
    "600.001,120",
    "7199.999,120",
    "7200,120",
    "7200.001,180"
  })
  @DisplayName("[SPEC-004 R-1 R-6] 운영 프로필의 경계와 기본 겹침을 선택한다")
  void profile(String duration, String length) {
    var i = PlanInput.production(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(duration));
    assertThat(i.chunkLength()).isEqualByComparingTo(length);
    assertThat(i.overlap()).isEqualByComparingTo("5");
  }

  @Test
  @DisplayName("[SPEC-004 R-2 R-6] 로컬 프로필은 영상 길이와 관계없이 60초와 5초 겹침이다")
  void local() {
    var i = PlanInput.local(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("8000"));
    assertThat(i.chunkLength()).isEqualByComparingTo("60");
    assertThat(i.overlap()).isEqualByComparingTo("5");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "0.000000001,1",
    "60,1",
    "120,2",
    "120.000000001,3"
  })
  @DisplayName("[SPEC-004 R-3 R-4 R-5] 짧은 영상과 정확한 배수 및 소수 잔여 구간을 빠짐없이 나눈다")
  void coverage(String seconds, int count) {
    var i = PlanInput.local(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal(seconds));
    var cs = ChunkPlanner.calculate(i);
    assertThat(cs).hasSize(count);
    var cursor = BigDecimal.ZERO;
    for (var c : cs) {
      assertThat(c.ownedStart()).isEqualByComparingTo(cursor);
      assertThat(c.ownedEnd()).isGreaterThan(c.ownedStart());
      assertThat(c.expandedStart()).isBetween(BigDecimal.ZERO, c.ownedStart());
      assertThat(c.expandedEnd()).isBetween(c.ownedEnd(), i.duration());
      cursor = c.ownedEnd();
    }
    assertThat(cursor).isEqualByComparingTo(i.duration());
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({
    "0,60,5",
    "-1,60,5",
    "60,0,5",
    "60,-1,5",
    "60,60,-1"
  })
  @DisplayName("[SPEC-004 R-7] 유효하지 않은 시간 입력을 거절한다")
  void invalid(String duration, String chunk, String overlap) {
    assertThatThrownBy(
            () ->
                new PlanInput(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    new BigDecimal(duration),
                    "custom",
                    new BigDecimal(chunk),
                    new BigDecimal(overlap)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
