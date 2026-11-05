package com.vlm.media.shared.domain;

import java.util.UUID;

/** 영상을 볼 수 있는 사람. */
public final class VideoAccess {

  private VideoAccess() {}

  /**
   * 삭제되지 않은 영상의 소유자이거나 ADMIN이면 볼 수 있다(SPEC-003 R-16, R-17). 볼 수 없는 이유는 구분하지 않는다.
   *
   * @param admin 요청자가 ADMIN 역할을 가졌는지
   */
  public static boolean canView(Video video, UUID userId, boolean admin) {
    return video.getStatus() != VideoStatus.DELETED && (admin || video.isOwnedBy(userId));
  }
}
