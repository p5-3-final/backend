package com.vlm.quality.evaluation.domain;

import org.jspecify.annotations.Nullable;

/** 기준 실행과 짝짓는 지표 키: 이름과 유형(R-18 "같은 이름·유형"). */
public record MetricKey(String name, @Nullable String eventType) {

  static MetricKey of(Metric metric) {
    return new MetricKey(metric.name(), metric.eventType());
  }
}
