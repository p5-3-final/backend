package com.vlm.media.infra.storage;

import com.vlm.media.domain.UploadStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Component
@RequiredArgsConstructor
class S3UploadStorage implements UploadStorage {

  private final MediaStorageProperties properties;
  private final S3Client s3;
  private final S3Presigner presigner;
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
  public String startMultipartUpload(String storageKey, String contentType) {
    return s3.createMultipartUpload(
            r -> r.bucket(properties.bucket()).key(storageKey).contentType(contentType))
        .uploadId();
  }

  @Override
  public List<PartTarget> presignParts(
      String storageKey, String uploadId, int partCount, Duration ttl) {
    return IntStream.rangeClosed(1, partCount)
        .mapToObj(
            partNumber ->
                new PartTarget(
                    partNumber,
                    presigner
                        .presignUploadPart(
                            r ->
                                r.signatureDuration(ttl)
                                    .uploadPartRequest(
                                        p ->
                                            p.bucket(properties.bucket())
                                                .key(storageKey)
                                                .uploadId(uploadId)
                                                .partNumber(partNumber)))
                        .url()
                        .toString()))
        .toList();
  }

  @Override
  public void completeMultipartUpload(
      String storageKey, String uploadId, List<UploadedPart> parts) {
    List<CompletedPart> completed =
        parts.stream()
            .sorted(Comparator.comparingInt(UploadedPart::partNumber))
            .map(p -> CompletedPart.builder().partNumber(p.partNumber()).eTag(p.eTag()).build())
            .toList();
    try {
      s3.completeMultipartUpload(
          r ->
              r.bucket(properties.bucket())
                  .key(storageKey)
                  .uploadId(uploadId)
                  .multipartUpload(m -> m.parts(completed)));
    } catch (S3Exception e) {
      // 조각이 빠졌거나(InvalidPart) 이미 합쳤거나 취소된 업로드(NoSuchUpload). 호출자가 파일 크기로 다시 판단한다.
      log.info("멀티파트 합치기 실패: key={}, status={}", storageKey, e.statusCode());
    }
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
