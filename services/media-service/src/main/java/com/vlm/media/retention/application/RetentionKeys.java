package com.vlm.media.retention.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** 보존 키 계산(SPEC-018 R-11, ADR-0011). 비밀 키 없이는 보존 데이터를 원래 영상과 다시 이을 수 없다. */
@Component
public class RetentionKeys {

  private static final String ALGORITHM = "HmacSHA256";
  // SPEC-018 R-12: HMAC-SHA256 출력 길이(32바이트)보다 짧은 키는 안전 여유가 줄어든다(RFC 2104 2절).
  private static final int MIN_KEY_BYTES = 32;

  private final SecretKeySpec key;

  /**
   * 비밀 키를 읽는다. 키가 없거나 약하면 기동을 멈춘다(SPEC-018 R-12). 동의한 영상을 보존 정보 없이 지우는 일을 막으려는 것이다.
   *
   * @throws IllegalStateException 키가 없거나 Base64가 아니거나 32바이트보다 짧을 때
   */
  public RetentionKeys(RetentionSettingsProperties properties) {
    this.key = new SecretKeySpec(decode(properties.hmacKey()), ALGORITHM);
  }

  private static byte[] decode(@Nullable String hmacKey) {
    if (hmacKey == null || hmacKey.isBlank()) {
      throw new IllegalStateException(
          "vlm.media.retention.hmac-key (MEDIA_RETENTION_HMAC_KEY) is not set");
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(hmacKey.strip());
    } catch (IllegalArgumentException e) {
      // 예외 메시지에 키 값이 들어가지 않게 원인만 남긴다(SPEC-018 NFR-1).
      throw new IllegalStateException("vlm.media.retention.hmac-key is not valid Base64", e);
    }
    if (bytes.length < MIN_KEY_BYTES) {
      throw new IllegalStateException(
          "vlm.media.retention.hmac-key must be at least " + MIN_KEY_BYTES + " bytes");
    }
    return bytes;
  }

  /** 영상의 보존 키. 같은 영상이면 언제 계산해도 같은 값이다(철회 때 다시 계산한다). */
  public String keyOf(UUID sourceId) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      // UUID#toString은 소문자다. 계약은 소문자 UUID 문자열로 계산한다고 정한다.
      return HexFormat.of()
          .formatHex(mac.doFinal(sourceId.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      // HmacSHA256은 모든 JDK가 제공해야 하는 알고리즘이라 여기에 오지 않는다.
      throw new IllegalStateException("HmacSHA256 is unavailable", e);
    }
  }
}
