package com.vlm.pipeline.config;

import com.vlm.pipeline.application.JobExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JobExecutionProperties.class)
public class JobExecutionConfiguration {
  @Configuration(proxyBeanMethods = false)
  @EnableScheduling
  @ConditionalOnProperty(name = "pipeline.execution.reconciliation-enabled", havingValue = "true")
  static class Scheduling {
    @Bean
    JobExecutionSchedule jobExecutionSchedule(
        JobExecutionService jobs, JobExecutionProperties properties) {
      return new JobExecutionSchedule(jobs, properties);
    }
  }

  static class JobExecutionSchedule {
    private static final Logger log = LoggerFactory.getLogger(JobExecutionSchedule.class);
    private final JobExecutionService jobs;
    private final JobExecutionProperties properties;

    JobExecutionSchedule(JobExecutionService jobs, JobExecutionProperties properties) {
      this.jobs = jobs;
      this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${pipeline.execution.interval-ms:30000}")
    void run() {
      int checked = jobs.reconcile(properties.batchSize());
      log.debug("Reconciled execution summaries: {}", checked);
    }
  }
}
