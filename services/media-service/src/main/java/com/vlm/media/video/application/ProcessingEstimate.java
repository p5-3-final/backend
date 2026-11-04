package com.vlm.media.video.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 예상 분석 처리 시간 계산(SPEC-007 R-20).
 *
 * @param estimateFactor 영상 1초를 처리하는 데 걸리는 초. 처리 실측 전이라 기본값 1은 임시값이며, 측정한 뒤 설정으로 바꾼다.
 */
@ConfigurationProperties("vlm.media.normalization")
public record ProcessingEstimate(@DefaultValue("1.0") BigDecimal estimateFactor) {

  private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1000);

  /** 영상 길이(밀리초) × 계수를 정수 초로 올림한다. */
  public int seconds(long durationMs) {
    return BigDecimal.valueOf(durationMs)
        .multiply(estimateFactor)
        .divide(MILLIS_PER_SECOND, 0, RoundingMode.CEILING)
        .intValueExact();
  }
}
