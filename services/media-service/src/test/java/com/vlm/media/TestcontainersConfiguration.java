package com.vlm.media;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
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

/**
 * 통합 테스트용 PostgreSQL, Kafka, SeaweedFS(S3 대체, ADR-0006).
 *
 * <p>SeaweedFS는 운영 S3와 같은 설정 키(endpoint, path-style)로 붙여 같은 코드 경로를 검증한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final String BUCKET = "vlm-media-test";
  static final String ACCESS_KEY = "testkey";
  static final String SECRET_KEY = "testsecret";

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

  @Bean
  GenericContainer<?> seaweedfsContainer() {
    return new GenericContainer<>("chrislusf/seaweedfs:4.48")
        // POST 정책 서명을 검증하려면 접근 키(identity) 설정이 있어야 한다(ADR-0006).
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("seaweedfs/s3.json"), "/cfg/s3.json")
        .withCommand("server", "-s3", "-s3.config=/cfg/s3.json", "-dir=/data")
        .withExposedPorts(8333)
        .waitingFor(Wait.forHttp("/").forPort(8333).forStatusCodeMatching(code -> code < 500))
        .withStartupTimeout(Duration.ofMinutes(2));
  }

  @Bean
  DynamicPropertyRegistrar storageProperties(GenericContainer<?> seaweedfsContainer) {
    String endpoint =
        "http://" + seaweedfsContainer.getHost() + ":" + seaweedfsContainer.getMappedPort(8333);
    // SDK 기본 자격 증명 체인은 시스템 속성을 환경변수보다 먼저 읽는다. 운영과 같은 체인을 그대로 쓰기 위해서다.
    System.setProperty("aws.accessKeyId", ACCESS_KEY);
    System.setProperty("aws.secretAccessKey", SECRET_KEY);
    createBucket(endpoint);
    return registry -> {
      registry.add("vlm.media.storage.bucket", () -> BUCKET);
      registry.add("vlm.media.storage.region", () -> "us-east-1");
      registry.add("vlm.media.storage.endpoint", () -> endpoint);
      registry.add("vlm.media.storage.public-endpoint", () -> endpoint);
      registry.add("vlm.media.storage.path-style", () -> "true");
    };
  }

  private static void createBucket(String endpoint) {
    try (S3Client s3 =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
            .build()) {
      // S3 포트가 열린 뒤에도 내부 filer가 준비될 때까지 잠깐 걸린다.
      RuntimeException last = null;
      for (int attempt = 0; attempt < 30; attempt++) {
        try {
          s3.createBucket(b -> b.bucket(BUCKET));
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
