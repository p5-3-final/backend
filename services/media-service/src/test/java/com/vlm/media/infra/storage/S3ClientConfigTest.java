package com.vlm.media.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ClientConfigTest {

  private static final StaticCredentialsProvider CREDENTIALS =
      StaticCredentialsProvider.create(AwsBasicCredentials.create("key", "secret"));

  @Test
  @DisplayName("주소를 비우면 S3 리전 기본 주소와 가상 호스트 방식으로 서명한다(운영)")
  void usesRegionalS3WhenEndpointIsEmpty() {
    MediaStorageProperties properties =
        new MediaStorageProperties("vlm-media", "ap-northeast-2", null, null, false);

    try (S3Presigner presigner = S3Clients.presigner(properties, CREDENTIALS);
        S3Client client = S3Clients.client(properties, CREDENTIALS)) {
      assertThat(presignedGet(presigner))
          .startsWith("https://vlm-media.s3.ap-northeast-2.amazonaws.com/sources/a/original?");
      assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
    }
  }

  @Test
  @DisplayName("주소를 넣으면 서버 호출은 내부 주소, 서명은 공개 주소의 path-style로 한다(로컬 SeaweedFS)")
  void usesConfiguredEndpointsWithPathStyle() {
    MediaStorageProperties properties =
        new MediaStorageProperties(
            "vlm-media", "us-east-1", "http://seaweedfs:8333", "http://localhost:8333", true);

    try (S3Presigner presigner = S3Clients.presigner(properties, CREDENTIALS);
        S3Client client = S3Clients.client(properties, CREDENTIALS)) {
      assertThat(presignedGet(presigner))
          .startsWith("http://localhost:8333/vlm-media/sources/a/original?");
      assertThat(client.serviceClientConfiguration().endpointOverride())
          .contains(URI.create("http://seaweedfs:8333"));
    }
  }

  @Test
  @DisplayName("공개 주소를 비우면 서버 호출 주소로 서명한다")
  void fallsBackToEndpointForSigning() {
    MediaStorageProperties properties =
        new MediaStorageProperties("vlm-media", "us-east-1", "http://localhost:8333", null, true);

    try (S3Presigner presigner = S3Clients.presigner(properties, CREDENTIALS)) {
      assertThat(presignedGet(presigner))
          .startsWith("http://localhost:8333/vlm-media/sources/a/original?");
    }
  }

  private static String presignedGet(S3Presigner presigner) {
    return presigner
        .presignGetObject(
            r ->
                r.signatureDuration(Duration.ofMinutes(1))
                    .getObjectRequest(g -> g.bucket("vlm-media").key("sources/a/original")))
        .url()
        .toString();
  }
}
