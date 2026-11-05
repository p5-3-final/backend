package com.vlm.media.shared.domain;

/** 영상 상태. 계약 {@code VideoStatus}에 삭제({@code DELETED})를 더한 것이다(삭제된 영상은 응답에 나오지 않는다). */
public enum VideoStatus {
  UPLOADING,
  UPLOADED,
  NORMALIZING,
  READY,
  REJECTED,
  EXPIRED,
  DELETED
}
