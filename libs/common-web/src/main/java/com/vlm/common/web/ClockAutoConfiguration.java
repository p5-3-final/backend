package com.vlm.common.web;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 현재 시각을 {@link Clock} 빈으로 등록한다. 만료·잠금처럼 시각에 기대는 규칙을 테스트에서 바꿀 수 있게 주입받아 쓴다.
 *
 * <p>서비스가 {@link Clock} 빈을 직접 등록하면 그것을 쓴다.
 */
@AutoConfiguration
public class ClockAutoConfiguration {

  // PostgreSQL TIMESTAMPTZ는 마이크로초까지 저장하므로 시계도 마이크로초 단위로 맞춘다.
  // 그러지 않으면 Linux(나노초 시계)에서 방금 만든 응답과 DB에서 다시 읽은 응답의 시각이 달라진다.
  @Bean
  @ConditionalOnMissingBean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
