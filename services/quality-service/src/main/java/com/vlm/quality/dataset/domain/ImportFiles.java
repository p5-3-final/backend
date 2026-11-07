package com.vlm.quality.dataset.domain;

import java.util.Optional;

/** 가져오기 버킷 읽기(SPEC-030 R-2). */
public interface ImportFiles {

  /** 매니페스트 파일 내용. 없으면 비어 있다. */
  Optional<byte[]> read(String key);
}
