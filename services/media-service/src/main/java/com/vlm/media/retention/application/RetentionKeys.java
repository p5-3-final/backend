package com.vlm.media.retention.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** 보존 키 계산(SPEC-018 R-11, ADR-0011). 비밀 키 없이는 보존 데이터를 원래 영상과 다시 이을 수 없다. */
@Component
public class RetentionKeys {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  public RetentionKeys(RetentionSettingsProperties properties) {
    this.key =
        new SecretKeySpec(java.util.Base64.getDecoder().decode(properties.hmacKey()), ALGORITHM);
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
