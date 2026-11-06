package com.vlm.media.retention.infra.scheduling;

import com.vlm.media.retention.application.RetentionExpiryJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 보존 기록 만료 작업. 테스트는 끄고 작업을 직접 부른다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    name = "vlm.media.jobs.scheduling-enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
class RetentionJobScheduling {

  private final RetentionExpiryJob expiryJob;

  // SPEC-018 R-17: 만료 뒤 24시간 안에 지운다. 1시간마다 돌아 여유를 둔다.
  @Scheduled(fixedDelayString = "${vlm.media.jobs.retention-expiry-interval-ms:3600000}")
  void expireRetainedVideos() {
    expiryJob.expire();
  }
}
