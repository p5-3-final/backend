package com.vlm.quality.export.domain;

import java.util.Optional;

/** 원본 버킷의 결과 파일(aggregates/v1/source-result) 읽기. */
public interface ResultFiles {

  /** 파일 내용. 없으면 비어 있다. */
  Optional<byte[]> read(String resultKey);
}
