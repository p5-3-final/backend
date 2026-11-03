package com.vlm.media.domain;

/** 정규화 거절 사유. 계약 {@code VideoRejectReason}과 같다(SPEC-007 R-7~R-12). */
public enum RejectReason {
  UNREADABLE,
  NO_VIDEO_STREAM,
  MISSING_METADATA,
  TOO_SHORT,
  TOO_LONG,
  DECODE_FAILED
}
