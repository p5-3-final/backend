package com.vlm.quality.alert.domain;

import com.vlm.common.error.BusinessException;
import com.vlm.quality.shared.domain.QualityErrorCode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** 알림 리포트 조회 조건(SPEC-029 R-28, 계약 to "from부터 최대 90일"). */
public record AlertReportQuery(LocalDate from, LocalDate to, @Nullable UUID cameraId) {

  public static final int MAX_DAYS = 90;

  /**
   * 조건을 검사해 만든다.
   *
   * @throws BusinessException 기간이 뒤집혔거나 90일을 넘으면 {@link QualityErrorCode#INVALID_REQUEST}
   */
  public static AlertReportQuery of(LocalDate from, LocalDate to, @Nullable UUID cameraId) {
    if (from.isAfter(to)) {
      throw new BusinessException(QualityErrorCode.INVALID_REQUEST, "from은 to보다 늦을 수 없습니다.");
    }
    if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
      throw new BusinessException(QualityErrorCode.INVALID_REQUEST, "기간은 90일을 넘을 수 없습니다.");
    }
    return new AlertReportQuery(from, to, cameraId);
  }
}
