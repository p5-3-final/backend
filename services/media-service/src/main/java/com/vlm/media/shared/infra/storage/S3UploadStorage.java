package com.vlm.media.shared.infra.storage;

import com.vlm.media.shared.domain.UploadStorage;
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
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
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
  public boolean deleteVideoFiles(
      String prefix, String storageKey, @Nullable String multipartUploadId) {
    try {
      if (multipartUploadId != null) {
        abortMultipartUpload(storageKey, multipartUploadId);
      }
      for (ListObjectsV2Response page :
          s3.listObjectsV2Paginator(r -> r.bucket(properties.bucket()).prefix(prefix))) {
        if (page.contents().isEmpty()) {
          continue;
        }
        List<ObjectIdentifier> objects =
            page.contents().stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .toList();
        DeleteObjectsResponse response =
            s3.deleteObjects(
                r -> r.bucket(properties.bucket()).delete(d -> d.objects(objects).quiet(true)));
        if (response.hasErrors() && !response.errors().isEmpty()) {
          log.warn("저장소 파일 삭제 실패: prefix={}, errors={}", prefix, response.errors().size());
          return false;
        }
      }
      return true;
    } catch (SdkException e) {
      log.warn("저장소 정리 실패: prefix={}", prefix, e);
      return false;
    }
  }

  // 업로드를 이미 합쳤거나 취소했으면 NoSuchUpload다. 지울 조각이 없으니 성공으로 본다.
  private void abortMultipartUpload(String storageKey, String uploadId) {
    try {
      s3.abortMultipartUpload(
          r -> r.bucket(properties.bucket()).key(storageKey).uploadId(uploadId));
    } catch (NoSuchUploadException e) {
      log.debug("이미 끝난 멀티파트 업로드: key={}", storageKey);
    }
  }

  @Override
  public String presignRead(
      String storageKey, String contentType, @Nullable String downloadFileName, Duration ttl) {
    return presigner
        .presignGetObject(
            r ->
                r.signatureDuration(ttl)
                    .getObjectRequest(
                        g -> {
                          // SPEC-006 R-3: 저장된 메타데이터는 업로드 경로에 따라 다를 수 있어 응답 형식을 주소에서 정한다.
                          g.bucket(properties.bucket())
                              .key(storageKey)
                              .responseContentType(contentType);
                          if (downloadFileName != null) {
                            g.responseContentDisposition(
                                ContentDispositions.attachment(downloadFileName));
                          }
                        }))
        .url()
        .toString();
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

  @Override
  public OptionalLong importObjectSize(String importKey) {
    try {
      return OptionalLong.of(
          s3.headObject(r -> r.bucket(importBucket()).key(importKey)).contentLength());
    } catch (NoSuchKeyException e) {
      return OptionalLong.empty();
    }
  }

  @Override
  public void copyFromImport(String importKey, String storageKey, Duration timeout) {
    try {
      s3.copyObject(
          c ->
              c.sourceBucket(importBucket())
                  .sourceKey(importKey)
                  .destinationBucket(properties.bucket())
                  .destinationKey(storageKey)
                  .overrideConfiguration(o -> o.apiCallTimeout(timeout)));
    } catch (ApiCallTimeoutException e) {
      throw new CopyTimeoutException(e);
    }
  }

  private String importBucket() {
    String bucket = properties.importBucket();
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalStateException("vlm.media.storage.import-bucket is not configured");
    }
    return bucket;
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
