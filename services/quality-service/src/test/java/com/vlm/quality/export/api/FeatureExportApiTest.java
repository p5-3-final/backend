package com.vlm.quality.export.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class FeatureExportApiTest {

  private static final String EXPORTS = "/api/v1/quality/feature-exports";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired GoldenFixtures golden;

  private final UUID me = UUID.randomUUID();

  @Test
  @DisplayName("[SPEC-030 R-14] Golden Set 내보내기를 요청하면 PENDING 내보내기를 202로 돌려주고 조회할 수 있다")
  void acceptsGoldenSetExport() throws Exception {
    UUID setId = golden.create("export-api");

    String body =
        request("{\"goldenSetId\":\"" + setId + "\"}")
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.goldenSetId").value(setId.toString()))
            .andExpect(jsonPath("$.from").value(nullValue()))
            .andExpect(jsonPath("$.to").value(nullValue()))
            .andExpect(jsonPath("$.fileKey").value(nullValue()))
            .andExpect(jsonPath("$.rowCount").value(0))
            .andExpect(jsonPath("$.sourceCount").value(0))
            .andExpect(jsonPath("$.finishedAt").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String exportId = body.replaceAll(".*\"exportId\":\"([^\"]+)\".*", "$1");

    mvc.perform(operator(HttpMethod.GET, me, EXPORTS + "/{exportId}", exportId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exportId").value(exportId))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 기간 내보내기는 from·to를 그대로 돌려준다")
  void acceptsPeriodExport() throws Exception {
    request("{\"from\":\"2026-09-01\",\"to\":\"2026-11-29\"}")
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.goldenSetId").value(nullValue()))
        .andExpect(jsonPath("$.from").value("2026-09-01"))
        .andExpect(jsonPath("$.to").value("2026-11-29"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"goldenSetId\":\"0192fb00-0000-7000-8000-000000000012\",\"from\":\"2026-11-01\",\"to\":\"2026-11-02\"}",
        "{\"from\":\"2026-11-01\"}",
        "{\"to\":\"2026-11-01\"}",
        "{\"from\":\"2026-11-02\",\"to\":\"2026-11-01\"}",
        "{\"from\":\"2026-09-01\",\"to\":\"2026-11-30\"}"
      })
  @DisplayName("[SPEC-030 R-15] 범위가 하나로 정해지지 않거나 기간이 90일을 넘으면 400 QUALITY_INVALID_REQUEST이고 행이 없다")
  void rejectsInvalidScope(String body) throws Exception {
    request(body)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("QUALITY_INVALID_REQUEST"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_exports", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 없는 Golden Set이면 404 QUALITY_GOLDEN_SET_NOT_FOUND이고 행이 없다")
  void rejectsUnknownGoldenSet() throws Exception {
    request("{\"goldenSetId\":\"" + UUID.randomUUID() + "\"}")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_NOT_FOUND"));

    assertThat(jdbc.queryForObject("SELECT count(*) FROM feature_exports", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[SPEC-030 R-14] 없는 내보내기를 조회하면 404 QUALITY_EXPORT_NOT_FOUND다")
  void unknownExport() throws Exception {
    mvc.perform(operator(HttpMethod.GET, me, EXPORTS + "/{exportId}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_EXPORT_NOT_FOUND"));
  }

  private ResultActions request(String body) throws Exception {
    return mvc.perform(
        operator(HttpMethod.POST, me, EXPORTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }
}
