package com.vlm.quality.stage.api;

import com.vlm.quality.stage.application.StageReport;
import com.vlm.quality.stage.application.StageReportService;
import com.vlm.quality.stage.domain.TrendQuery;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import vlm.contracts.quality.api.StageReportsApi;
import vlm.contracts.quality.model.SourceStageReport;
import vlm.contracts.quality.model.StageStat;
import vlm.contracts.quality.model.StageTrend;
import vlm.contracts.quality.model.StageTrendDaysInner;

/** 처리 단계 리포트 API(계약 tag stage-reports, SPEC-029 R-22, R-24). */
@RestController
@RequiredArgsConstructor
class StageReportController implements StageReportsApi {

  private final StageReportService reports;

  @Override
  public ResponseEntity<SourceStageReport> getSourceStageReport(
      UUID xUserId, String xUserRoles, UUID sourceId) {
    StageReport report = reports.sourceReport(sourceId);
    return ResponseEntity.ok(
        new SourceStageReport(
            report.sourceId(),
            Math.toIntExact(report.recordCount()),
            report.stages().stream()
                .map(
                    s ->
                        new StageStat(
                            s.path(),
                            s.stageId(),
                            Math.toIntExact(s.count()),
                            BigDecimal.valueOf(s.totalMs()),
                            BigDecimal.valueOf(s.p50Ms()),
                            BigDecimal.valueOf(s.p95Ms()),
                            Math.toIntExact(s.errorCount())))
                .toList()));
  }

  @Override
  public ResponseEntity<StageTrend> getStageTrend(
      UUID xUserId,
      String xUserRoles,
      Object stageId,
      LocalDate from,
      LocalDate to,
      @Nullable String versionKey,
      @Nullable String versionValue) {
    // 생성기는 stageId($ref oneOf const 목록)를 Object로 만든다. 쿼리 값은 문자열이다.
    TrendQuery query = TrendQuery.of(stageId.toString(), from, to, versionKey, versionValue);
    return ResponseEntity.ok(
        new StageTrend(
            query.stageId(),
            query.from(),
            query.to(),
            query.versionKey(),
            query.versionValue(),
            reports.trend(query).stream()
                .map(
                    d ->
                        new StageTrendDaysInner(
                            d.date(),
                            Math.toIntExact(d.count()),
                            BigDecimal.valueOf(d.p50Ms()),
                            BigDecimal.valueOf(d.p95Ms()),
                            Math.toIntExact(d.errorCount())))
                .toList()));
  }
}
