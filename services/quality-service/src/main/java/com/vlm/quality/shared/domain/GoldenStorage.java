package com.vlm.quality.shared.domain;

/** Golden Set 저장소. 확정 버전의 근거 프레임 사본과 매니페스트를 둔다(ADR-0011). */
public interface GoldenStorage {

  /** 원본 버킷의 블러 사본을 Golden Set 버킷으로 서버 쪽 복사한다. */
  void copyFromSource(String sourceKey, String goldenKey);

  /** Golden Set 버킷에 JSON 문서를 쓴다. */
  void putJson(String goldenKey, byte[] json);

  /** Golden Set 버킷의 문서를 읽는다. */
  byte[] read(String goldenKey);
}
