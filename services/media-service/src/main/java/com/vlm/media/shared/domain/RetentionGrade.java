package com.vlm.media.shared.domain;

/** 영상 보존 등급(ADR-0011 overview 보존 표). */
public enum RetentionGrade {
  /** 사용자가 올린 영상. */
  STANDARD,
  /** 외부 데이터셋 영상(SPEC-030 R-5). */
  DATASET
}
