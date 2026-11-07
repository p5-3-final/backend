package com.vlm.quality.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 가져오기·내보내기 전용 실행기(SPEC-030 NFR-2).
 *
 * <p>스레드 하나다. 요청 스레드(Tomcat)와 스케줄러 스레드를 쓰지 않아, 영상 1,000개 가져오기 중에도 다른 API 응답이 밀리지 않는다. 일은 DB 행에서
 * 가져가므로 Pod마다 하나씩 돌아도 같은 일을 두 번 하지 않는다(SKIP LOCKED).
 */
@Configuration(proxyBeanMethods = false)
class BackgroundJobConfig {

  static final String EXECUTOR = "qualityBackgroundJobExecutor";

  @Bean(name = EXECUTOR, destroyMethod = "shutdownNow")
  ExecutorService qualityBackgroundJobExecutor() {
    return Executors.newSingleThreadExecutor(
        Thread.ofPlatform().name("quality-background-job").daemon(true).factory());
  }
}
