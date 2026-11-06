package com.vlm.quality.config;

import com.vlm.quality.review.application.StaleAssignmentJob;
import com.vlm.quality.stage.application.DailyStageAggregationJob;
import com.vlm.quality.stage.application.StageTimingPartitionJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** quality-service 주기 작업(plan "한 Pod만 도는 주기 작업"). 테스트는 끄고 작업을 직접 부른다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "vlm.quality.jobs.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
class QualityJobScheduling {

  private final StaleAssignmentJob staleAssignmentJob;
  private final DailyStageAggregationJob dailyStageAggregationJob;
  private final StageTimingPartitionJob stageTimingPartitionJob;

  // R-10: 10분마다
  @Scheduled(fixedDelayString = "${vlm.quality.jobs.stale-assignment-interval-ms:600000}")
  void releaseStaleAssignments() {
    staleAssignmentJob.releaseStale();
  }

  // R-23: 매일 00:15 UTC, 전날 하루
  @Scheduled(cron = "0 15 0 * * *", zone = "UTC")
  void aggregateStageTimings() {
    dailyStageAggregationJob.aggregateYesterday();
  }

  // R-25: 매일 00:30 UTC
  @Scheduled(cron = "0 30 0 * * *", zone = "UTC")
  void manageStageTimingPartitions() {
    stageTimingPartitionJob.manage();
  }
}
