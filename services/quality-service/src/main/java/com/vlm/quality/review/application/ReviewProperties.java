package com.vlm.quality.review.application;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 검토 큐 설정.
 *
 * @param sampleRates 유형별 표본 비율(R-2). {@code default}는 설정이 없는 유형의 비율이다
 * @param assignmentTimeout 라벨 없이 맡아 둘 수 있는 시간(R-10)
 */
@ConfigurationProperties("vlm.quality.review")
public record ReviewProperties(Map<String, Double> sampleRates, Duration assignmentTimeout) {

  private static final String DEFAULT_RATE = "default";
  private static final double FALLBACK_RATE = 0.05;

  public ReviewProperties {
    sampleRates = Map.copyOf(sampleRates);
  }

  /** 유형의 표본 비율. 유형 설정이 없으면 {@code default}, 그것도 없으면 spec 기본값 0.05다. */
  public double sampleRate(String eventType) {
    Double rate = sampleRates.get(eventType);
    if (rate != null) {
      return rate;
    }
    return sampleRates.getOrDefault(DEFAULT_RATE, FALLBACK_RATE);
  }
}
