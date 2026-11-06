package com.vlm.quality.stage.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrendQueryTest {

  private static final LocalDate FROM = LocalDate.of(2026, 8, 1);

  @Test
  @DisplayName("[SPEC-029 R-24] 기간은 from부터 최대 90일이고 from이 to보다 늦으면 거절한다")
  void validatesRange() {
    assertThat(TrendQuery.of("analyzer.judge", FROM, FROM.plusDays(89), null, null).to())
        .isEqualTo(FROM.plusDays(89));
    assertInvalid(() -> TrendQuery.of("analyzer.judge", FROM, FROM.plusDays(90), null, null));
    assertInvalid(() -> TrendQuery.of("analyzer.judge", FROM, FROM.minusDays(1), null, null));
  }

  @Test
  @DisplayName("[SPEC-029 R-24] versionValue만 있거나(R-24) versionKey만 있으면(D-28) 거절한다")
  void requiresVersionKeyAndValueTogether() {
    assertInvalid(() -> TrendQuery.of("analyzer.judge", FROM, FROM, null, "gpt"));
    assertInvalid(() -> TrendQuery.of("analyzer.judge", FROM, FROM, "modelId", null));
    assertThat(TrendQuery.of("analyzer.judge", FROM, FROM, "modelId", "gpt").versionValue())
        .isEqualTo("gpt");
  }

  private static void assertInvalid(Runnable query) {
    assertThatThrownBy(query::run).isInstanceOf(BusinessException.class);
  }
}
