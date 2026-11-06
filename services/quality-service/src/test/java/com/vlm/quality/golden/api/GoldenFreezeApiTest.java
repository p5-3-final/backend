package com.vlm.quality.golden.api;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.support.ContractSchemas;
import com.vlm.quality.support.GoldenFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class GoldenFreezeApiTest {

  private static final UUID OPERATOR = UUID.randomUUID();
  private static final String VERSIONS = "/api/v1/quality/golden-sets/{setId}/versions";

  @Autowired MockMvc mvc;
  @Autowired GoldenFixtures golden;
  @Autowired ReviewFixtures reviews;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @Test
  @DisplayName("[SPEC-029 R-14] 라벨 항목 3개를 확정하면 버전 1, 계약에 맞는 매니페스트, 복사한 프레임이 생긴다")
  void freezesFirstVersion() throws Exception {
    UUID setId = golden.create("freeze-set");
    for (int i = 0; i < 3; i++) {
      UUID source = UUID.randomUUID();
      List<String> keys =
          List.of(
              StorageFixtures.putSourceFrame(source, 0), StorageFixtures.putSourceFrame(source, 1));
      golden.addEntry(setId, reviews.labeled(source, keys, ReviewFixtures.CORRECT));
    }

    JsonNode version = golden.freeze(setId);

    assertThat(version.get("version").asInt()).isEqualTo(1);
    assertThat(version.get("entryCount").asInt()).isEqualTo(3);
    String manifestKey = "golden/" + setId + "/v1/manifest.json";
    assertThat(version.get("manifestKey").asText()).isEqualTo(manifestKey);
    JsonNode manifest = json.readTree(StorageFixtures.goldenObject(manifestKey));
    assertThat(ContractSchemas.validate("quality/v1/golden-manifest.schema.json", manifest))
        .isEmpty();
    assertThat(manifest.get("entries")).hasSize(3);
    JsonNode first = manifest.get("entries").get(0);
    String frame = first.get("frameKeys").get(1).asText();
    assertThat(frame)
        .isEqualTo("golden/" + setId + "/v1/frames/" + first.get("entryId").asText() + "/1.jpg");
    assertThat(new String(StorageFixtures.goldenObject(frame), StandardCharsets.UTF_8))
        .endsWith("/f1.jpg");
    mvc.perform(operator(HttpMethod.GET, OPERATOR, "/api/v1/quality/golden-sets/{id}", setId))
        .andExpect(jsonPath("$.latestVersion").value(1));
  }

  @Test
  @DisplayName("[SPEC-029 R-14] 두 번째 확정은 버전 2이고 목록은 버전 내림차순이다")
  void freezesNextVersion() throws Exception {
    UUID setId = golden.create("twice-set");
    UUID source = UUID.randomUUID();
    golden.addEntry(
        setId,
        reviews.labeled(
            source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT));
    golden.freeze(setId);

    assertThat(golden.freeze(setId).get("version").asInt()).isEqualTo(2);
    mvc.perform(operator(HttpMethod.GET, OPERATOR, VERSIONS, setId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].version").value(2))
        .andExpect(jsonPath("$.items[1].version").value(1));
  }

  @Test
  @DisplayName("[SPEC-029 R-15] 초안이 비면 409 QUALITY_GOLDEN_EMPTY이고 버전이 생기지 않는다")
  void rejectsEmptyDraft() throws Exception {
    UUID setId = golden.create("empty-set");

    mvc.perform(operator(HttpMethod.POST, OPERATOR, VERSIONS, setId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_EMPTY"));
    assertThat(versionCount()).isZero();
    mvc.perform(operator(HttpMethod.POST, OPERATOR, VERSIONS, UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_SET_NOT_FOUND"));
  }

  @Test
  @DisplayName("[SPEC-029 R-15] 확정 진행 중이면 409 QUALITY_GOLDEN_FREEZE_IN_PROGRESS, 오래된 표시는 무시한다")
  void rejectsWhileFreezing() throws Exception {
    UUID setId = golden.create("busy-set");
    UUID source = UUID.randomUUID();
    golden.addEntry(
        setId,
        reviews.labeled(
            source, List.of(StorageFixtures.putSourceFrame(source, 0)), ReviewFixtures.CORRECT));
    markFreezing(setId, Instant.now());

    mvc.perform(operator(HttpMethod.POST, OPERATOR, VERSIONS, setId))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("QUALITY_GOLDEN_FREEZE_IN_PROGRESS"));
    assertThat(versionCount()).isZero();

    markFreezing(setId, Instant.now().minus(Duration.ofHours(1)));
    assertThat(golden.freeze(setId).get("version").asInt()).isEqualTo(1);
  }

  private void markFreezing(UUID setId, Instant at) {
    jdbc.update(
        "UPDATE golden_sets SET freeze_started_at = ? WHERE set_id = ?", Timestamp.from(at), setId);
  }

  private int versionCount() {
    Integer count = jdbc.queryForObject("SELECT count(*) FROM golden_set_versions", Integer.class);
    return count == null ? 0 : count;
  }
}
