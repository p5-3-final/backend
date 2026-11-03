package com.vlm.pipeline.config;

import com.vlm.pipeline.application.DispatchRecoveryService;
import com.vlm.pipeline.application.DispatchService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DispatchProperties.class)
public class DispatchConfiguration {
  @Bean
  Gauge dispatchRecoveryGauge(MeterRegistry registry, DispatchRecoveryService recovery) {
    return Gauge.builder(
            "pipeline.dispatch.recovery.required", recovery, DispatchRecoveryService::recoveryCount)
        .description("Held slots awaiting verified execution termination")
        .register(registry);
  }

  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(name = "pipeline.dispatch.enabled", havingValue = "true")
  static class Scheduling {
    @Bean
    DispatchSchedule dispatchSchedule(
        DispatchService dispatch, DispatchRecoveryService recovery, DispatchProperties properties) {
      return new DispatchSchedule(dispatch, recovery, properties);
    }
  }

  static class DispatchSchedule {
    private final DispatchService dispatch;
    private final DispatchRecoveryService recovery;
    private final DispatchProperties properties;

    DispatchSchedule(
        DispatchService dispatch, DispatchRecoveryService recovery, DispatchProperties properties) {
      this.dispatch = dispatch;
      this.recovery = recovery;
      this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pipeline.dispatch.interval-ms:1000}")
    void run() {
      recovery.markExpired();
      for (int i = 0; i < properties.batchSize(); i++) if (dispatch.dispatchOne().isEmpty()) break;
    }
  }
}
