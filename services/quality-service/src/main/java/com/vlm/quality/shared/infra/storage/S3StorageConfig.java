package com.vlm.quality.shared.infra.storage;

import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 저장소 클라이언트 빈. 자격 증명은 SDK 기본 체인(운영 IRSA, 로컬 환경변수)이다.
 *
 * <p>서명 클라이언트는 브라우저가 닿는 주소로 서명한다. 서명한 주소의 호스트가 곧 브라우저가 접속할 곳이기 때문이다.
 */
@Configuration(proxyBeanMethods = false)
class S3StorageConfig {

  @Bean(destroyMethod = "close")
  DefaultCredentialsProvider awsCredentialsProvider() {
    return DefaultCredentialsProvider.builder().build();
  }

  @Bean(destroyMethod = "close")
  S3Client s3Client(QualityStorageProperties properties, DefaultCredentialsProvider credentials) {
    var builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .forcePathStyle(properties.pathStyle());
    endpoint(properties.endpoint()).ifPresent(builder::endpointOverride);
    return builder.build();
  }

  @Bean(destroyMethod = "close")
  S3Presigner s3Presigner(
      QualityStorageProperties properties, DefaultCredentialsProvider credentials) {
    var builder =
        S3Presigner.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(
                S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
    endpoint(properties.publicEndpoint())
        .or(() -> endpoint(properties.endpoint()))
        .ifPresent(builder::endpointOverride);
    return builder.build();
  }

  private static Optional<URI> endpoint(@Nullable String value) {
    return Optional.ofNullable(value).filter(v -> !v.isBlank()).map(URI::create);
  }
}
