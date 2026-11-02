package com.vlm.media.domain;

/** 파일 크기로 정한 업로드 방식. */
public record UploadPlan(Method method, long partSizeBytes, int partCount) {

  public enum Method {
    SINGLE,
    MULTIPART
  }

  /** 기준 크기 이하는 단일, 넘으면 조각 크기로 나눈 멀티파트(SPEC-003 R-2, R-3). */
  public static UploadPlan of(long sizeBytes, long singleMaxBytes, long partSizeBytes) {
    if (sizeBytes <= singleMaxBytes) {
      return new UploadPlan(Method.SINGLE, 0, 0);
    }
    int partCount = Math.toIntExact((sizeBytes + partSizeBytes - 1) / partSizeBytes);
    return new UploadPlan(Method.MULTIPART, partSizeBytes, partCount);
  }
}
