package com.vlm.quality.alert.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.hamcrest.Matchers.closeTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.stage.application.StageTimingIngestService;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.StageFixtures;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class AlertReportApiTest {

  // percentile_cont 보간은 부동소수점이라 CPU(FMA 사용 여부)에 따라 마지막 자리가 다르다.
  private static final double EPSILON = 1e-6;
  private static final String REPORTS = "/api/v1/quality/alert-reports";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired StageTimingIngestService ingest;

  private final UUID source = UUID.randomUUID();
  private final UUID camera = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-029 R-28] 날짜별 상태 건수, 1차 알림 지연과 판정 지연의 p50·p95를 돌려주고 카메라로 거른다")
  void returnsDailyAlertReport() throws Exception {
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(2);
    Instant at = day.atTime(9, 0).toInstant(ZoneOffset.UTC);
    // 판정 지연 1000·2000·3000ms → p50 2000, p95 2900. 1차 알림 지연 100·200·300ms → p50 200, p95 290.
    alert(camera, "confirmed", at, at.plusMillis(1000), 100);
    alert(camera, "dismissed", at, at.plusMillis(2000), 200);
    alert(camera, "confirmed", at, at.plusMillis(3000), 300);
    alert(camera, "pending", at, null, null);
    alert(UUID.randomUUID(), "dismissed", at, at.plusMillis(9000), 900);

    mvc.perform(
            operator(
                HttpMethod.GET,
                UUID.randomUUID(),
                REPORTS + "?from={f}&to={t}&cameraId={c}",
                day.minusDays(1),
                day.plusDays(1),
                camera))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cameraId").value(camera.toString()))
        .andExpect(jsonPath("$.days.length()").value(1))
        .andExpect(jsonPath("$.days[0].date").value(day.toString()))
        .andExpect(jsonPath("$.days[0].pendingCount").value(1))
        .andExpect(jsonPath("$.days[0].confirmedCount").value(2))
        .andExpect(jsonPath("$.days[0].dismissedCount").value(1))
        .andExpect(jsonPath("$.days[0].judgeP50Ms", closeTo(2000.0, EPSILON)))
        .andExpect(jsonPath("$.days[0].judgeP95Ms", closeTo(2900.0, EPSILON)))
        .andExpect(jsonPath("$.days[0].firstAlertP50Ms", closeTo(200.0, EPSILON)))
        .andExpect(jsonPath("$.days[0].firstAlertP95Ms", closeTo(290.0, EPSILON)));

    mvc.perform(
            operator(
                HttpMethod.GET,
                UUID.randomUUID(),
                REPORTS + "?from={f}&to={t}",
                day.minusDays(1),
                day.plusDays(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cameraId").isEmpty())
        .andExpect(jsonPath("$.days[0].dismissedCount").value(2));
  }

  @Test
  @DisplayName("[SPEC-029 R-28] 판정 전인 후보만 있는 날은 지연 값이 null이다")
  void nullLatencyWithoutResolution() throws Exception {
    LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(1);
    alert(camera, "pending", day.atTime(1, 0).toInstant(ZoneOffset.UTC), null, null);

    mvc.perform(operator(HttpMethod.GET, UUID.randomUUID(), REPORTS + "?from={f}&to={t}", day, day))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.days[0].pendingCount").value(1))
        .andExpect(jsonPath("$.days[0].judgeP50Ms").isEmpty())
        .andExpect(jsonPath("$.days[0].firstAlertP95Ms").isEmpty());
  }

  @Test
  @DisplayName("[SPEC-029 R-28] 기간이 90일을 넘거나 from이 to보다 늦으면 400 QUALITY_INVALID_REQUEST다")
  void rejectsInvalidPeriod() throws Exception {
    for (String query :
        new String[] {"from=2026-01-01&to=2026-04-01", "from=2026-02-02&to=2026-02-01"}) {
      mvc.perform(operator(HttpMethod.GET, UUID.randomUUID(), REPORTS + "?" + query))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));
    }
  }

  private void alert(
      UUID cameraId,
      String alertStatus,
      Instant pendingAt,
      @Nullable Instant resolvedAt,
      @Nullable Integer firstAlertMs) {
    UUID candidate = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO alert_events (candidate_id, source_id, camera_id, event_type, severity,"
            + " pending_at, resolved_at, status, evidence_frame_keys) VALUES (?, ?, ?, 'fall',"
            + " 'high', ?, ?, ?, '[]'::jsonb)",
        candidate,
        source,
        cameraId,
        Timestamp.from(pendingAt),
        resolvedAt == null ? null : Timestamp.from(resolvedAt),
        alertStatus);
    if (firstAlertMs != null) {
      ingest.ingest(
          StageFixtures.batch(
              StageFixtures.alertTiming(
                  source, candidate, pendingAt.minusMillis(firstAlertMs), firstAlertMs)));
    }
  }
}
