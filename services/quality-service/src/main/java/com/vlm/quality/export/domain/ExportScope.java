package com.vlm.quality.export.domain;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 내보낼 영상 범위. Golden Set이나 기간 중 하나다(SPEC-030 R-14, R-15). */
public record ExportScope(
    @Nullable UUID goldenSetId, @Nullable LocalDate from, @Nullable LocalDate to) {

  /** R-14: 기간은 양 끝 날짜를 포함해 최대 90일이다. */
  static final long MAX_PERIOD_DAYS = 90;

  /**
   * 요청 값을 범위로 만든다.
   *
   * @throws BusinessException 범위가 하나로 정해지지 않거나 기간이 잘못되면 {@link QualityErrorCode#INVALID_REQUEST}
   */
  public static ExportScope of(
      @Nullable UUID goldenSetId, @Nullable LocalDate from, @Nullable LocalDate to) {
    boolean hasPeriod = from != null || to != null;
    if (goldenSetId != null) {
      if (hasPeriod) {
        throw invalid("goldenSetId와 기간 중 하나만 지정해야 합니다.");
      }
      return new ExportScope(goldenSetId, null, null);
    }
    if (from == null || to == null) {
      throw invalid("goldenSetId나 from·to를 지정해야 합니다.");
    }
    if (from.isAfter(to)) {
      throw invalid("from이 to보다 늦습니다.");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
      throw invalid("기간은 최대 " + MAX_PERIOD_DAYS + "일입니다.");
    }
    return new ExportScope(null, from, to);
  }

  public boolean isGoldenSet() {
    return goldenSetId != null;
  }

  private static BusinessException invalid(String message) {
    return new BusinessException(QualityErrorCode.INVALID_REQUEST, message);
  }
}
