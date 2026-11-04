package com.vlm.media.video.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ProcessingEstimateTest {

  @ParameterizedTest(name = "{0}ms × {1} = {2}초")
  @CsvSource({"612480, 1.0, 613", "1000, 1.0, 1", "1, 1.0, 1", "60000, 0.5, 30", "60001, 0.5, 31"})
  @DisplayName("[SPEC-007 R-20] 예상 처리 시간은 영상 길이 × 처리 계수를 정수 초로 올림한다")
  void roundsUpToWholeSeconds(long durationMs, String factor, int expected) {
    ProcessingEstimate estimate = new ProcessingEstimate(new BigDecimal(factor));

    assertThat(estimate.seconds(durationMs)).isEqualTo(expected);
  }

  @Test
  @DisplayName("[SPEC-007 R-20] 처리 계수 기본값은 1이다(실측 전 임시값)")
  void defaultFactorIsOne() {
    ProcessingEstimate estimate =
        new Binder(new MapConfigurationPropertySource())
            .bindOrCreate("vlm.media.normalization", ProcessingEstimate.class);

    assertThat(estimate.seconds(4_000)).isEqualTo(4);
  }
}
