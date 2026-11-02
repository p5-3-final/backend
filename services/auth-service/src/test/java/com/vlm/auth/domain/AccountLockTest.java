package com.vlm.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountLockTest {

  private static final Instant NOW = Instant.parse("2026-11-02T03:00:00Z");

  private final User user = User.signUp("user@example.com", "hash", "테스터");

  @Test
  @DisplayName("[SPEC-001 R-29] 연속 4회 실패까지는 잠그지 않는다")
  void notLockedAfterFourFailures() {
    fail(4);

    assertThat(user.isLocked(NOW)).isFalse();
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 연속 5회 실패하면 15분 동안 잠근다")
  void lockedForFifteenMinutesAfterFiveFailures() {
    fail(5);

    assertThat(user.isLocked(NOW)).isTrue();
    assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1))).isTrue();
    assertThat(user.isLocked(NOW.plus(Duration.ofMinutes(15)))).isFalse();
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 로그인에 성공하면 실패 횟수를 초기화한다")
  void successResetsFailures() {
    fail(4);
    user.recordLoginSuccess();
    fail(4);

    assertThat(user.isLocked(NOW)).isFalse();
  }

  @Test
  @DisplayName("[SPEC-001 R-29] 잠금이 풀린 뒤에는 다시 5회 실패해야 잠근다")
  void countsAgainAfterLockExpires() {
    fail(5);
    Instant afterLock = NOW.plus(Duration.ofMinutes(15));

    user.recordLoginFailure(afterLock);

    assertThat(user.isLocked(afterLock)).isFalse();
  }

  private void fail(int times) {
    for (int i = 0; i < times; i++) {
      user.recordLoginFailure(NOW);
    }
  }
}
