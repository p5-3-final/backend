package com.vlm.media.domain;

import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;

/** 영상 파일 저장소. 구현은 S3 API 하나이고 운영 S3와 로컬 SeaweedFS를 설정으로 고른다(ADR-0006). */
public interface UploadStorage {

  /** 신고한 크기의 파일만 받는 브라우저 POST 업로드 방법을 만든다(SPEC-003 R-2). */
  SingleUploadTarget prepareSingleUpload(
      String storageKey, String contentType, long sizeBytes, Instant expiresAt);

  /** 저장소에 있는 파일 크기. 파일이 없으면 비어 있다. */
  OptionalLong objectSize(String storageKey);

  /** 브라우저 POST 업로드 방법. */
  record SingleUploadTarget(String url, Map<String, String> fields, Instant expiresAt) {}
}
