package com.vlm.quality.dataset.infra.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.Registered;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.RegistrationResult;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.Rejected;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry.VideoRegistration;
import com.vlm.quality.dataset.domain.ImportErrorCodes;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MediaDatasetClientTest {

  private static final String PATH = "/internal/v1/media/dataset-videos";
  private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
  private static final UUID VIDEO = UUID.fromString("0192fd20-0000-5000-8000-000000000001");

  private WireMockServer media;
  private MediaDatasetClient client;
  private final VideoRegistration request =
      new VideoRegistration(
          UUID.randomUUID(),
          "clip-0001",
          UUID.randomUUID(),
          "imports/ds/videos/clip-0001.mp4",
          "video/mp4");

  @BeforeEach
  void start() {
    media = new WireMockServer(wireMockConfig().dynamicPort());
    media.start();
    client = client(10);
  }

  @AfterEach
  void stop() {
    media.stop();
  }

  @Test
  @DisplayName("[SPEC-030 R-4] 계약 요청 형식으로 X-Caller·X-Request-Deadline과 함께 부르고 받은 videoId를 돌려준다")
  void registersWithContractRequest() {
    stubStatus(201);

    RegistrationResult result = client.register(request);

    assertThat(result).isEqualTo(new Registered(VIDEO));
    LoggedRequest sent = media.findAll(postRequestedFor(urlEqualTo(PATH))).get(0);
    assertThat(sent.getHeader("X-Caller")).isEqualTo("quality-service");
    assertThat(Long.parseLong(sent.getHeader("X-Request-Deadline")))
        .isEqualTo(NOW.plusSeconds(60).toEpochMilli());
    var report = contract().validateRequest(asRequest(sent));
    assertThat(report.getMessages()).isEmpty();
  }

  @Test
  @DisplayName("[SPEC-030 R-6] 이미 등록한 영상(200)도 videoId를 돌려준다")
  void acceptsExistingVideo() {
    stubStatus(200);

    assertThat(client.register(request)).isEqualTo(new Registered(VIDEO));
  }

  @Test
  @DisplayName("[SPEC-030 R-4] 503이면 지수 백오프로 2회까지 다시 불러 성공하면 videoId를 돌려준다")
  void retriesUnavailable() {
    media.stubFor(
        post(PATH)
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("second"));
    media.stubFor(
        post(PATH)
            .inScenario("retry")
            .whenScenarioStateIs("second")
            .willReturn(aResponse().withStatus(503))
            .willSetStateTo("third"));
    media.stubFor(
        post(PATH).inScenario("retry").whenScenarioStateIs("third").willReturn(created(201)));

    assertThat(client.register(request)).isEqualTo(new Registered(VIDEO));
    assertThat(media.findAll(postRequestedFor(urlEqualTo(PATH)))).hasSize(3);
  }

  @Test
  @DisplayName("[SPEC-030 R-7] 재시도 뒤에도 503이면 QUALITY_IMPORT_MEDIA_UNAVAILABLE로 끝낸다")
  void reportsUnavailableAfterRetries() {
    media.stubFor(post(PATH).willReturn(aResponse().withStatus(503)));

    RegistrationResult result = client.register(request);

    assertThat(result).isInstanceOf(Rejected.class);
    assertThat(((Rejected) result).code()).isEqualTo(ImportErrorCodes.MEDIA_UNAVAILABLE);
    assertThat(media.findAll(postRequestedFor(urlEqualTo(PATH)))).hasSize(3);
  }

  @Test
  @DisplayName(
      "[SPEC-030 R-7] 404는 QUALITY_IMPORT_VIDEO_NOT_FOUND, 400은 QUALITY_IMPORT_VIDEO_REJECTED이고 다시 부르지 않는다")
  void doesNotRetryClientErrors() {
    media.stubFor(
        post(PATH)
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing("clip-0001"))
            .willReturn(problem(404, "MEDIA_IMPORT_OBJECT_NOT_FOUND")));
    media.stubFor(
        post(PATH)
            .withRequestBody(
                com.github.tomakehurst.wiremock.client.WireMock.containing("clip-0002"))
            .willReturn(problem(400, "MEDIA_UNSUPPORTED_FORMAT")));

    RegistrationResult missing = client.register(request);
    RegistrationResult rejected =
        client.register(
            new VideoRegistration(
                request.importId(),
                "clip-0002",
                request.ownerId(),
                "imports/ds/videos/clip-0002.webm",
                "video/webm"));

    assertThat(((Rejected) missing).code()).isEqualTo(ImportErrorCodes.VIDEO_NOT_FOUND);
    assertThat(((Rejected) rejected).code()).isEqualTo(ImportErrorCodes.VIDEO_REJECTED);
    assertThat(((Rejected) rejected).message()).contains("MEDIA_UNSUPPORTED_FORMAT");
    assertThat(media.findAll(postRequestedFor(urlEqualTo(PATH)))).hasSize(2);
  }

  @Test
  @DisplayName("[SPEC-030 R-4] 실패가 이어져 서킷이 열리면 media를 부르지 않고 바로 QUALITY_IMPORT_MEDIA_UNAVAILABLE이다")
  void opensCircuit() {
    client = client(2);
    media.stubFor(post(PATH).willReturn(aResponse().withStatus(503)));
    client.register(request);
    int callsBeforeOpen = media.findAll(postRequestedFor(urlEqualTo(PATH))).size();

    RegistrationResult result = client.register(request);

    assertThat(((Rejected) result).code()).isEqualTo(ImportErrorCodes.MEDIA_UNAVAILABLE);
    assertThat(media.findAll(postRequestedFor(urlEqualTo(PATH)))).hasSize(callsBeforeOpen);
  }

  private MediaDatasetClient client(int circuitMinimumCalls) {
    var properties =
        new MediaClientProperties(
            "http://localhost:" + media.port(),
            Duration.ofSeconds(2),
            Duration.ofSeconds(60),
            3,
            Duration.ofMillis(10),
            circuitMinimumCalls,
            Duration.ofSeconds(30));
    return new MediaDatasetClient(
        RestClient.builder(), properties, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private void stubStatus(int status) {
    media.stubFor(
        post(PATH).withHeader("X-Caller", equalTo("quality-service")).willReturn(created(status)));
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder created(
      int status) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/json")
        .withBody("{\"videoId\":\"" + VIDEO + "\",\"status\":\"UPLOADED\",\"sizeBytes\":31457280}");
  }

  private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder problem(
      int status, String code) {
    return aResponse()
        .withStatus(status)
        .withHeader("Content-Type", "application/problem+json")
        .withBody(
            "{\"type\":\"about:blank\",\"title\":\"x\",\"status\":"
                + status
                + ",\"code\":\""
                + code
                + "\"}");
  }

  private static OpenApiInteractionValidator contract() {
    Path yaml =
        Path.of(
            Objects.requireNonNull(System.getProperty("vlm.contracts.dir")),
            "openapi",
            "media-service.yaml");
    return OpenApiInteractionValidator.createForSpecificationUrl(yaml.toString()).build();
  }

  private static Request asRequest(LoggedRequest sent) {
    SimpleRequest.Builder builder =
        SimpleRequest.Builder.post(sent.getUrl()).withBody(sent.getBodyAsString());
    for (String name : List.of("Content-Type", "X-Caller", "X-Request-Deadline")) {
      builder.withHeader(name, sent.getHeader(name));
    }
    return builder.build();
  }
}
