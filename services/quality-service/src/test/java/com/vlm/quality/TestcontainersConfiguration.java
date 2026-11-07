package com.vlm.quality;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

/** 통합 테스트용 PostgreSQL, Kafka, SeaweedFS(S3 대체, ADR-0006). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final String SOURCE_BUCKET = "vlm-media-test";
  public static final String GOLDEN_BUCKET = "vlm-golden-test";
  public static final String IMPORT_BUCKET = "vlm-imports-test";
  public static final String EXPORT_BUCKET = "vlm-exports-test";
  static final String ACCESS_KEY = "testkey";
  static final String SECRET_KEY = "testsecret";

  private static volatile String storageEndpoint = "";
  // media-service를 대신한다(SPEC-030 R-4). 컨텍스트 하나를 모든 테스트가 같이 쓰므로 서버도 하나다.
  private static final WireMockServer MEDIA = new WireMockServer(wireMockConfig().dynamicPort());

  /** media-service 대역. 테스트마다 {@code resetAll()}로 비운다. */
  public static WireMockServer media() {
    return MEDIA;
  }

  /** 테스트가 저장소를 직접 확인할 때 쓰는 클라이언트를 만든다. */
  public static S3Client s3() {
    return S3Client.builder()
        .endpointOverride(URI.create(storageEndpoint))
        .region(Region.US_EAST_1)
        .forcePathStyle(true)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();
  }

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>("postgres:17-alpine");
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafkaContainer() {
    return new KafkaContainer("apache/kafka:4.3.2");
  }

  // 운영은 토픽을 미리 만들고 자동 생성을 끈다. 테스트도 리스너가 붙기 전에 만들어 둔다.
  @Bean
  KafkaAdmin.NewTopics qualityTopics() {
    return new KafkaAdmin.NewTopics(
        new NewTopic("event.analyzed", 1, (short) 1),
        new NewTopic("video.deleted", 1, (short) 1),
        new NewTopic("stage.timing", 1, (short) 1),
        new NewTopic("alert.raised", 1, (short) 1));
  }

  @Bean
  GenericContainer<?> seaweedfsContainer() {
    return new GenericContainer<>("chrislusf/seaweedfs:4.48")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("seaweedfs/s3.json"), "/cfg/s3.json")
        // 버킷마다 볼륨 묶음을 새로 만든다. 기본 볼륨 수 상한(8)으로는 세 번째 버킷부터 쓸 볼륨이 모자라 500이 난다.
        .withCommand(
            "server",
            "-s3",
            "-s3.config=/cfg/s3.json",
            "-dir=/data",
            "-volume.max=64",
            "-master.volumeSizeLimitMB=64")
        .withExposedPorts(8333)
        .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500))
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  @Bean
  DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfsContainer) {
    String endpoint =
        "http://" + seaweedfsContainer.getHost() + ":" + seaweedfsContainer.getMappedPort(8333);
    storageEndpoint = endpoint;
    // SDK 기본 자격 증명 체인은 시스템 속성을 환경변수보다 먼저 읽는다. 운영과 같은 체인을 그대로 쓰기 위해서다.
    System.setProperty("aws.accessKeyId", ACCESS_KEY);
    System.setProperty("aws.secretAccessKey", SECRET_KEY);
    try (S3Client s3 = s3()) {
      for (String bucket : List.of(SOURCE_BUCKET, GOLDEN_BUCKET, IMPORT_BUCKET, EXPORT_BUCKET)) {
        createBucket(s3, bucket);
      }
    }
    if (!MEDIA.isRunning()) {
      MEDIA.start();
    }
    return registry -> {
      registry.add("vlm.quality.media.base-url", () -> "http://localhost:" + MEDIA.port());
      registry.add("vlm.quality.storage.import-bucket", () -> IMPORT_BUCKET);
      registry.add("vlm.quality.storage.export-bucket", () -> EXPORT_BUCKET);
      registry.add("vlm.quality.storage.source-bucket", () -> SOURCE_BUCKET);
      registry.add("vlm.quality.storage.golden-bucket", () -> GOLDEN_BUCKET);
      registry.add("vlm.quality.storage.region", () -> "us-east-1");
      registry.add("vlm.quality.storage.endpoint", () -> endpoint);
      registry.add("vlm.quality.storage.public-endpoint", () -> endpoint);
      registry.add("vlm.quality.storage.path-style", () -> "true");
    };
  }

  private static void createBucket(S3Client s3, String bucket) {
    // S3 포트가 열린 뒤에도 내부 filer가 준비될 때까지 잠깐 걸린다.
    RuntimeException last = null;
    for (int attempt = 0; attempt < 30; attempt++) {
      try {
        s3.createBucket(b -> b.bucket(bucket));
        return;
      } catch (BucketAlreadyOwnedByYouException e) {
        return;
      } catch (RuntimeException e) {
        last = e;
        sleep();
      }
    }
    throw new IllegalStateException("SeaweedFS 버킷을 만들지 못했다", last);
  }

  private static void sleep() {
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
