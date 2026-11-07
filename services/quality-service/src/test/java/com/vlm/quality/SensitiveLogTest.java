package com.vlm.quality;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vlm.quality.export.application.FeatureExportService;
import com.vlm.quality.export.application.SourceCompleted;
import com.vlm.quality.export.application.SourceResultService;
import com.vlm.quality.export.domain.ExportScope;
import com.vlm.quality.support.DatasetFixtures;
import com.vlm.quality.support.Events;
import com.vlm.quality.support.ExportFixtures;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SensitiveLogTest {

  private static final Pattern SIGNATURE = Pattern.compile("X-Amz-Signature=([^&]+)");

  private static final String LOGGER = "com.vlm";

  // 속성으로 로그 수준을 바꾸면 Spring 컨텍스트와 저장소 컨테이너가 하나 더 뜨고, 테스트 보조 클라이언트
  // (TestcontainersConfiguration.s3())가 새 컨테이너를 가리켜 다른 테스트가 어긋난다. 같은 컨텍스트에서 수준만 바꾼다.
  @Autowired LoggingSystem logging;
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired ReviewFixtures reviews;
  @Autowired DatasetFixtures datasets;
  @Autowired ExportFixtures exportFixtures;
  @Autowired SourceResultService sourceResults;
  @Autowired FeatureExportService featureExports;

  @BeforeEach
  void enableDebug() {
    logging.setLogLevel(LOGGER, LogLevel.DEBUG);
  }

  @AfterEach
  void restoreLevel() {
    logging.setLogLevel(LOGGER, null);
  }

  @Test
  @DisplayName("[SPEC-029 R-5] 근거 프레임 서명 주소의 서명을 로그에 남기지 않는다")
  void doesNotLogSignedUrls(CapturedOutput output) throws Exception {
    UUID source = UUID.randomUUID();
    UUID itemId =
        reviews.reviewRequired(
            source,
            "fall",
            List.of(
                StorageFixtures.putSourceFrame(source, 0),
                StorageFixtures.putSourceFrame(source, 1)));

    String body =
        mvc.perform(
                operator(
                    HttpMethod.GET, UUID.randomUUID(), "/api/v1/quality/review-items/{id}", itemId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> signatures = new ArrayList<>();
    for (JsonNode url : json.readTree(body).get("evidenceFrameUrls")) {
      signatures.add(signatureOf(url.asText()));
    }

    assertThat(signatures).hasSize(2).doesNotContain("");
    for (String signature : signatures) {
      assertThat(output.getAll()).doesNotContain(signature);
    }
  }

  @Test
  @DisplayName("[SPEC-030 T031] 가져오기 시작·끝·실패를 importId와 함께 남기고 매니페스트 내용은 남기지 않는다")
  void logsImportsWithoutManifestContent(CapturedOutput output) {
    TestcontainersConfiguration.media().resetAll();
    String marker = "manifest-secret-" + UUID.randomUUID();
    ObjectNode manifest = DatasetFixtures.manifest("log-import", "clip-0001");
    ((ObjectNode) manifest.get("origin")).put("url", "https://example.org/" + marker);
    ((ObjectNode) manifest.get("videos").get(0).get("labels").get(0)).put("severity", "high");
    DatasetFixtures.upload("log-import", manifest);
    DatasetFixtures.mediaAnswers("clip-0001", 201);

    UUID done = datasets.importNow("log-import", 0.2, 7, UUID.randomUUID());
    UUID failed = datasets.importNow("log-missing", 0.2, 7, UUID.randomUUID());

    assertThat(output.getAll())
        .contains("Accepted dataset import importId=" + done)
        .contains("Dataset import started importId=" + done)
        .contains("Dataset import completed importId=" + done)
        .contains("Dataset import failed importId=" + failed)
        .doesNotContain(marker);
  }

  @Test
  @DisplayName("[SPEC-030 T031] 내보내기 시작·끝을 exportId와 함께 남기고 결과 파일 내용은 남기지 않는다")
  void logsExportsWithoutFileContent(CapturedOutput output) {
    String marker = "result-secret-" + UUID.randomUUID();
    UUID source = UUID.randomUUID();
    UUID jobId = UUID.randomUUID();
    ObjectNode result = ExportFixtures.resultFile(source, jobId, 1);
    ((ObjectNode) result.get("risks").get(0)).put("reason", marker);
    String key = Events.resultKey(source, jobId);
    StorageFixtures.putSource(key, result.toString().getBytes(StandardCharsets.UTF_8));
    sourceResults.record(new SourceCompleted(source, jobId, 1, key));
    LocalDate today = LocalDate.now(ZoneOffset.UTC);

    UUID exportId =
        featureExports.request(ExportScope.of(null, today, today), UUID.randomUUID()).exportId();
    exportFixtures.runWorker();

    assertThat(output.getAll())
        .contains("Accepted feature export exportId=" + exportId)
        .contains("Feature export started exportId=" + exportId)
        .contains("Feature export completed exportId=" + exportId)
        .doesNotContain(marker);
  }

  private static String signatureOf(String url) {
    Matcher matcher = SIGNATURE.matcher(URI.create(url).getRawQuery());
    return matcher.find() ? matcher.group(1) : "";
  }
}
