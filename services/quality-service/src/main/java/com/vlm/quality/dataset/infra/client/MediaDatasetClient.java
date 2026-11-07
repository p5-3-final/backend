package com.vlm.quality.dataset.infra.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.vlm.quality.dataset.domain.DatasetVideoRegistry;
import com.vlm.quality.dataset.domain.ImportErrorCodes;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * media-service {@code POST /internal/v1/media/dataset-videos} 클라이언트(SPEC-030 R-4).
 *
 * <p>등록은 videoId가 (importId, externalId)로 결정적이라 다시 불러도 같은 결과다. 그래서 읽기 전용 호출 규약과 같이 5xx·연결 실패·시간 초과만
 * 지수 백오프로 다시 부른다. 4xx는 다시 불러도 같으므로 바로 영상별 오류로 돌려준다(R-7). media가 계속 실패하면 서킷을 열어 남은 영상을 기다리지 않고 실패로
 * 넘긴다.
 *
 * <p>계약 생성 클라이언트 대신 작은 RestClient 호출로 둔다. 작업 하나만 쓰고, 생성 모델({@code vlm.contracts..})은 api 레이어에서만 쓰는
 * 규칙(ArchitectureTest)이 있다. 요청 형식은 {@code MediaDatasetClientTest}가 계약으로 검증한다.
 */
@Component
class MediaDatasetClient implements DatasetVideoRegistry {

  static final String CALLER = "quality-service";
  private static final String PATH = "/internal/v1/media/dataset-videos";
  private static final double BACKOFF_MULTIPLIER = 2.0;
  private static final float CIRCUIT_FAILURE_RATE = 50;

  private static final Logger log = LoggerFactory.getLogger(MediaDatasetClient.class);

  private final RestClient rest;
  private final MediaClientProperties properties;
  private final Clock clock;
  private final Retry retry;
  private final CircuitBreaker circuit;

  MediaDatasetClient(RestClient.Builder builder, MediaClientProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
    var factory =
        new JdkClientHttpRequestFactory(
            // 내부 동기 호출은 HTTP/1.1이다(architecture.md). 평문 h2c 업그레이드를 시도하지 않게 고정한다.
            HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build());
    factory.setReadTimeout(properties.readTimeout());
    this.rest = builder.clone().baseUrl(properties.baseUrl()).requestFactory(factory).build();
    this.retry =
        Retry.of(
            "media-dataset-videos",
            RetryConfig.custom()
                .maxAttempts(properties.maxAttempts())
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(
                        properties.retryWait(), BACKOFF_MULTIPLIER))
                .retryOnException(MediaDatasetClient::transientFailure)
                .build());
    this.circuit =
        CircuitBreaker.of(
            "media-dataset-videos",
            CircuitBreakerConfig.custom()
                .minimumNumberOfCalls(properties.circuitMinimumCalls())
                .slidingWindowSize(Math.max(properties.circuitMinimumCalls(), 1))
                .failureRateThreshold(CIRCUIT_FAILURE_RATE)
                .waitDurationInOpenState(properties.circuitOpenDuration())
                .recordException(MediaDatasetClient::transientFailure)
                .build());
  }

  @Override
  public RegistrationResult register(VideoRegistration request) {
    Supplier<JsonNode> call =
        Retry.decorateSupplier(
            retry, CircuitBreaker.decorateSupplier(circuit, () -> post(request)));
    try {
      JsonNode body = call.get();
      return new Registered(UUID.fromString(body.get("videoId").asText()));
    } catch (CallNotPermittedException e) {
      return new Rejected(ImportErrorCodes.MEDIA_UNAVAILABLE, "media-service 호출 서킷이 열려 있음");
    } catch (RestClientResponseException e) {
      return rejected(request, e);
    } catch (ResourceAccessException e) {
      log.warn("media-service call failed externalId={}", request.externalId(), e);
      return new Rejected(ImportErrorCodes.MEDIA_UNAVAILABLE, "media-service에 닿지 않음");
    }
  }

  private JsonNode post(VideoRegistration request) {
    // 내부 호출 규약: 받는 쪽이 남은 시간을 알도록 시도마다 마감을 다시 정한다.
    long deadline = clock.instant().plus(properties.readTimeout()).toEpochMilli();
    JsonNode body =
        rest.post()
            .uri(PATH)
            .header("X-Caller", CALLER)
            .header("X-Request-Deadline", Long.toString(deadline))
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "importId", request.importId(),
                    "externalId", request.externalId(),
                    "ownerId", request.ownerId(),
                    "importKey", request.importKey(),
                    "contentType", request.contentType()))
            .retrieve()
            .body(JsonNode.class);
    if (body == null || body.get("videoId") == null) {
      throw new IllegalStateException("media-service returned no videoId");
    }
    return body;
  }

  private static Rejected rejected(VideoRegistration request, RestClientResponseException e) {
    HttpStatusCode status = e.getStatusCode();
    if (status.value() == 404) {
      return new Rejected(ImportErrorCodes.VIDEO_NOT_FOUND, request.importKey() + " 없음");
    }
    if (status.is4xxClientError()) {
      String code = problemCode(e);
      return new Rejected(
          ImportErrorCodes.VIDEO_REJECTED,
          "media-service " + status.value() + (code == null ? "" : " " + code));
    }
    return new Rejected(
        ImportErrorCodes.MEDIA_UNAVAILABLE, "media-service " + status.value() + " (재시도 뒤)");
  }

  private static @Nullable String problemCode(RestClientResponseException e) {
    try {
      JsonNode problem = e.getResponseBodyAs(JsonNode.class);
      JsonNode code = problem == null ? null : problem.get("code");
      return code == null ? null : code.asText();
    } catch (RuntimeException ignored) {
      // 본문이 Problem Details가 아니면 상태 코드만 남긴다.
      return null;
    }
  }

  // 5xx·연결 실패·시간 초과만 일시적 실패로 본다. 4xx는 다시 불러도 같다.
  private static boolean transientFailure(Throwable error) {
    return error instanceof HttpServerErrorException || error instanceof ResourceAccessException;
  }
}
