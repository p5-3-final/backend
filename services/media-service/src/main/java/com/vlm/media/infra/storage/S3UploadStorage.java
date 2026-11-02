package com.vlm.media.infra.storage;

import com.vlm.media.domain.UploadStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@RequiredArgsConstructor
class S3UploadStorage implements UploadStorage {

  private final MediaStorageProperties properties;
  private final S3Client s3;
  private final AwsCredentialsProvider credentials;
  private final Clock clock;

  @Override
  public SingleUploadTarget prepareSingleUpload(
      String storageKey, String contentType, long sizeBytes, Instant expiresAt) {
    Map<String, String> fields =
        PostPolicySigner.sign(
            new PostPolicySigner.PostPolicy(
                properties.bucket(),
                storageKey,
                contentType,
                sizeBytes,
                expiresAt,
                properties.region()),
            credentials.resolveCredentials(),
            clock.instant());
    return new SingleUploadTarget(bucketUrl(), fields, expiresAt);
  }

  @Override
  public OptionalLong objectSize(String storageKey) {
    try {
      return OptionalLong.of(
          s3.headObject(r -> r.bucket(properties.bucket()).key(storageKey)).contentLength());
    } catch (NoSuchKeyException e) {
      return OptionalLong.empty();
    }
  }

  /** 브라우저가 POST할 버킷 주소. 서명용 공개 주소를 기준으로 path-style이면 경로에, 아니면 호스트에 버킷을 넣는다. */
  private String bucketUrl() {
    var utilities =
        S3Utilities.builder()
            .region(Region.of(properties.region()))
            .s3Configuration(
                S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build());
    S3Clients.endpoint(properties.publicEndpoint())
        .or(() -> S3Clients.endpoint(properties.endpoint()))
        .ifPresent(utilities::endpoint);
    String objectUrl =
        utilities.build().getUrl(r -> r.bucket(properties.bucket()).key("x")).toString();
    // getUrl은 객체 주소를 준다. 끝의 객체 키만 떼어 버킷 주소로 쓴다.
    return objectUrl.substring(0, objectUrl.length() - 1);
  }
}
