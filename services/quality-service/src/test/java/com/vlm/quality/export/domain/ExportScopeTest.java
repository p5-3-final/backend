package com.vlm.quality.export.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ExportScopeTest {

  private static final UUID SET = UUID.fromString("0192f0f0-0000-7000-8000-000000000001");
  private static final LocalDate DAY = LocalDate.of(2026, 11, 1);

  @Test
  @DisplayName("[SPEC-030 R-14] goldenSetId만 있으면 Golden Set 범위다")
  void goldenSetScope() {
    ExportScope scope = ExportScope.of(SET, null, null);

    assertThat(scope.goldenSetId()).isEqualTo(SET);
    assertThat(scope.isGoldenSet()).isTrue();
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 기간은 양 끝을 포함해 90일까지 받는다")
  void periodUpTo90Days() {
    ExportScope scope = ExportScope.of(null, DAY, DAY.plusDays(89));

    assertThat(scope.isGoldenSet()).isFalse();
    assertThat(scope.from()).isEqualTo(DAY);
    assertThat(scope.to()).isEqualTo(DAY.plusDays(89));
    assertThat(ExportScope.of(null, DAY, DAY).isGoldenSet()).isFalse();
  }

  static Stream<Arguments> invalid() {
    return Stream.of(
        Arguments.of("둘 다 있음", SET, DAY, DAY),
        Arguments.of("둘 다 없음", null, null, null),
        Arguments.of("from만", null, DAY, null),
        Arguments.of("to만", null, null, DAY),
        Arguments.of("Golden Set과 from", SET, DAY, null),
        Arguments.of("from > to", null, DAY, DAY.minusDays(1)),
        Arguments.of("91일", null, DAY, DAY.plusDays(90)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalid")
  @DisplayName("[SPEC-030 R-15] 범위가 하나로 정해지지 않거나 기간이 90일을 넘으면 QUALITY_INVALID_REQUEST")
  void rejectsInvalid(String name, UUID set, LocalDate from, LocalDate to) {
    assertThatThrownBy(() -> ExportScope.of(set, from, to))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(QualityErrorCode.INVALID_REQUEST));
  }
}
