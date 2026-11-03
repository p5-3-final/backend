package com.vlm.pipeline;

import static org.assertj.core.api.Assertions.*;

import com.vlm.pipeline.domain.StopConfirmation;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DispatchRecoveryCommandTest {
  @Test
  @DisplayName("[SPEC-010 R-11] 운영 명령에 필요한 실행·확인·증거 참조를 검증한다")
  void command() {
    String execution = UUID.randomUUID().toString(), confirmation = UUID.randomUUID().toString();
    var c =
        StopConfirmation.parse(
            new String[] {execution, confirmation, "incident:123/worker-stopped"});
    assertThat(c.executionId()).isEqualTo(UUID.fromString(execution));
    assertThat(c.confirmationId()).isEqualTo(UUID.fromString(confirmation));
    assertThat(c.evidence()).isEqualTo("incident:123/worker-stopped");
    assertThatThrownBy(() -> StopConfirmation.parse(new String[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> StopConfirmation.parse(new String[] {"bad", confirmation, "stop:ok"}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "stop:1\nstop:2", "stop:1\rstop:2"})
  @DisplayName("[SPEC-010 R-12] 비어 있거나 여러 줄인 종료 증거는 거부한다")
  void evidence(String evidence) {
    assertThatThrownBy(() -> new StopConfirmation(UUID.randomUUID(), UUID.randomUUID(), evidence))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
