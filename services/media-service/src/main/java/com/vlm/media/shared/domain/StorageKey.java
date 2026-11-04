package com.vlm.media.shared.domain;

import java.util.UUID;

/** 저장소 경로 규칙. 경로는 영상 ID로만 만든다(SPEC-003 R-6). 사용자가 보낸 파일 이름을 쓰면 경로 조작이나 다른 영상과의 충돌이 생길 수 있다. */
public final class StorageKey {

  private StorageKey() {}

  /** 원본 파일 키. */
  public static String original(UUID videoId) {
    return prefix(videoId) + "original";
  }

  /** 영상 하나의 모든 파일이 들어가는 접두사. 삭제할 때 쓴다. */
  public static String prefix(UUID videoId) {
    return "sources/" + videoId + "/";
  }
}
