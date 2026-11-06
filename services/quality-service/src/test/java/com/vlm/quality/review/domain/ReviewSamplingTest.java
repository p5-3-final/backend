package com.vlm.quality.review.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReviewSamplingTest {

  private static final UUID EVENT = UUID.fromString("0192f100-0000-5000-8000-0000000000f1");

  @Test
  @DisplayName("[SPEC-029 R-2] 해시는 SHA-256(eventId) 앞 8바이트를 [0,1)로 옮긴 값이다")
  void hashesEventId() {
    // python: int.from_bytes(sha256(b"0192f100-0000-5000-8000-0000000000f1").digest()[:8]) / 2**64
    assertThat(ReviewSampling.hash(EVENT)).isCloseTo(0.5551658756455328, within(1e-12));
  }

  @Test
  @DisplayName("[SPEC-029 R-2] 비율 0이면 고르지 않고 1이면 모두 고른다")
  void respectsRateBounds() {
    assertThat(ReviewSampling.sampled(EVENT, 0.0)).isFalse();
    assertThat(ReviewSampling.sampled(EVENT, 1.0)).isTrue();
  }

  @Test
  @DisplayName("[SPEC-029 R-2] 같은 eventId는 항상 같은 결과이고 비율만큼 고른다")
  void isDeterministicAndProportional() {
    long sampled =
        IntStream.range(0, 20_000)
            .mapToObj(i -> UUID.nameUUIDFromBytes(("event-" + i).getBytes(StandardCharsets.UTF_8)))
            .filter(id -> ReviewSampling.sampled(id, 0.05))
            .count();

    assertThat(ReviewSampling.sampled(EVENT, 0.5)).isEqualTo(ReviewSampling.sampled(EVENT, 0.5));
    assertThat(sampled).isBetween(800L, 1200L);
  }
}
