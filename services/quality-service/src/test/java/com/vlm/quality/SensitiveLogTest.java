package com.vlm.quality;

import static com.vlm.quality.support.QualityApi.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlm.quality.support.IntegrationTest;
import com.vlm.quality.support.ReviewFixtures;
import com.vlm.quality.support.StorageFixtures;
import java.net.URI;
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

  private static String signatureOf(String url) {
    Matcher matcher = SIGNATURE.matcher(URI.create(url).getRawQuery());
    return matcher.find() ? matcher.group(1) : "";
  }
}
