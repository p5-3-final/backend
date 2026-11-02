package com.vlm.media.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

  // 업로드 만료와 주소 유효 시간을 테스트에서 바꿀 수 있도록 현재 시각을 Clock으로 주입한다.
  // PostgreSQL TIMESTAMPTZ는 마이크로초까지 저장하므로 시계도 마이크로초 단위로 맞춘다.
  // 그러지 않으면 Linux(나노초 시계)에서 방금 만든 응답과 DB에서 다시 읽은 응답의 시각이 달라진다.
  @Bean
  Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
  }
}
