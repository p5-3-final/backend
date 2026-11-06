package com.vlm.quality.alert.api;

import com.vlm.quality.alert.application.AlertReportService;
import com.vlm.quality.alert.domain.AlertDay;
import com.vlm.quality.alert.domain.AlertReportQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.AlertReportsApi;
import vlm.contracts.quality.model.AlertReport;
import vlm.contracts.quality.model.AlertReportDaysInner;

/** 긴급 알림 리포트 API(계약 tag alert-reports, SPEC-029 R-28). */
@RestController
@RequiredArgsConstructor
class AlertReportController implements AlertReportsApi {

  private final AlertReportService reports;

  @Override
  public ResponseEntity<AlertReport> getAlertReport(
      UUID xUserId, String xUserRoles, LocalDate from, LocalDate to, @Nullable UUID cameraId) {
    AlertReportQuery query = AlertReportQuery.of(from, to, cameraId);
    return ResponseEntity.ok(
        new AlertReport(
            query.from(),
            query.to(),
            query.cameraId(),
            reports.report(query).stream().map(AlertReportController::day).toList()));
  }

  private static AlertReportDaysInner day(AlertDay day) {
    return new AlertReportDaysInner(
        day.date(),
        Math.toIntExact(day.pendingCount()),
        Math.toIntExact(day.confirmedCount()),
        Math.toIntExact(day.dismissedCount()),
        decimal(day.firstAlertP50Ms()),
        decimal(day.firstAlertP95Ms()),
        decimal(day.judgeP50Ms()),
        decimal(day.judgeP95Ms()));
  }

  private static @Nullable BigDecimal decimal(@Nullable Double value) {
    return value == null ? null : BigDecimal.valueOf(value);
  }
}
