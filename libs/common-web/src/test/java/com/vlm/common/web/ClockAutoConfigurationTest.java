package com.vlm.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ClockAutoConfiguration.class));

  @Test
  @DisplayName("UTC 시계를 마이크로초 단위로 등록한다")
  void registersMicrosecondUtcClock() {
    runner.run(
        context -> {
          Clock clock = context.getBean(Clock.class);
          assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
          for (int i = 0; i < 100; i++) {
            Instant now = clock.instant();
            assertThat(now).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
          }
        });
  }

  @Test
  @DisplayName("서비스가 시계를 직접 등록하면 그 시계를 쓴다")
  void backsOffWhenServiceDefinesClock() {
    Clock fixed = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    runner
        .withBean(Clock.class, () -> fixed)
        .run(context -> assertThat(context).getBean(Clock.class).isSameAs(fixed));
  }
}
