package com.vlm.media.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;

/** 영상 파일 저장소. 구현은 S3 API 하나이고 운영 S3와 로컬 SeaweedFS를 설정으로 고른다(ADR-0006). */
public interface UploadStorage {

  /** 신고한 크기의 파일만 받는 브라우저 POST 업로드 방법을 만든다(SPEC-003 R-2). */
  SingleUploadTarget prepareSingleUpload(
      String storageKey, String contentType, long sizeBytes, Instant expiresAt);

  /** 멀티파트 업로드를 시작하고 업로드 ID를 돌려준다(SPEC-003 R-3). */
  String startMultipartUpload(String storageKey, String contentType);

  /** 조각마다 {@code ttl} 동안 유효한 서명 PUT 주소를 만든다(SPEC-003 NFR-6). */
  List<PartTarget> presignParts(String storageKey, String uploadId, int partCount, Duration ttl);

  /**
   * 올라간 조각을 하나의 파일로 합친다(SPEC-003 R-8). 조각이 맞지 않거나 업로드가 없어도 예외를 던지지 않는다. 결과는 호출자가 {@link
   * #objectSize}로 확인한다.
   */
  void completeMultipartUpload(String storageKey, String uploadId, List<UploadedPart> parts);

  /**
   * 영상 하나의 파일을 모두 지운다. 끝나지 않은 멀티파트 업로드가 있으면 먼저 취소한다(SPEC-003 R-20).
   *
   * @param prefix 영상 하나의 파일이 들어가는 접두사({@link StorageKey#prefix})
   * @param multipartUploadId 멀티파트로 올린 영상이면 그 업로드 ID
   * @return 모두 지웠으면 true. 실패하면 false이고 호출자가 다음에 다시 시도한다
   */
  boolean deleteVideoFiles(String prefix, String storageKey, @Nullable String multipartUploadId);

  /** 저장소에 있는 파일 크기. 파일이 없으면 비어 있다. */
  OptionalLong objectSize(String storageKey);

  /** 업로드 방법. */
  sealed interface UploadTarget permits SingleUploadTarget, MultipartUploadTarget {}

  /** 브라우저 POST 업로드 방법. */
  record SingleUploadTarget(String url, Map<String, String> fields, Instant expiresAt)
      implements UploadTarget {}

  /** 멀티파트 업로드 방법. */
  record MultipartUploadTarget(long partSizeBytes, List<PartTarget> parts, Instant expiresAt)
      implements UploadTarget {}

  /** 조각 하나의 업로드 주소. */
  record PartTarget(int partNumber, String url) {}

  /** 브라우저가 올린 조각. {@code eTag}는 조각 PUT 응답의 ETag 헤더 값이다. */
  record UploadedPart(int partNumber, String eTag) {}
}
