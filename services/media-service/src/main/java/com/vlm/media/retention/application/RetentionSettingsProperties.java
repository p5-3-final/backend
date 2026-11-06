package com.vlm.media.retention.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학습용 보존 설정(SPEC-018).
 *
 * @param hmacKey 보존 키를 계산하는 비밀 키(Base64). media-service만 갖는다(ADR-0011).
 */
@ConfigurationProperties("vlm.media.retention")
public record RetentionSettingsProperties(@Nullable String hmacKey) {

  // 비밀 키가 설정 출력·오류 메시지에 남지 않게 한다(SPEC-018 NFR-1).
  @Override
  public String toString() {
    return "RetentionSettingsProperties[hmacKey=***]";
  }
}
