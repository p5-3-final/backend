package com.vlm.media.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TimeConfig {

  // 업로드 만료와 주소 유효 시간을 테스트에서 바꿀 수 있도록 현재 시각을 Clock으로 주입한다.
  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
