package com.vlm.media.infra.storage;

import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 설정값으로 S3 클라이언트를 만든다. 저장소 종류에 따른 코드 분기는 두지 않는다. */
final class S3Clients {

  private S3Clients() {}

  /** 서버가 저장소를 호출할 때 쓰는 클라이언트(조회, 멀티파트 시작·합치기, 삭제). */
  static S3Client client(MediaStorageProperties properties, AwsCredentialsProvider credentials) {
    var builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .forcePathStyle(properties.pathStyle());
    endpoint(properties.endpoint()).ifPresent(builder::endpointOverride);
    return builder.build();
  }

  /** 브라우저에 줄 업로드 주소를 서명하는 클라이언트. 서명한 주소의 호스트가 곧 브라우저가 접속할 곳이다. */
  static S3Presigner presigner(
      MediaStorageProperties properties, AwsCredentialsProvider credentials) {
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

  static Optional<URI> endpoint(@Nullable String value) {
    return Optional.ofNullable(value).filter(v -> !v.isBlank()).map(URI::create);
  }
}
