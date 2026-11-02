package com.vlm.media.config;

import com.vlm.media.application.StorageCleanupJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** media-service 주기 작업. 테스트는 끄고 작업을 직접 부른다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "vlm.media.jobs.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
class MediaJobScheduling {

  private final StorageCleanupJob storageCleanupJob;

  @Scheduled(fixedDelayString = "${vlm.media.jobs.cleanup-interval-ms:60000}")
  void cleanUpStorage() {
    storageCleanupJob.cleanUp();
  }
}
