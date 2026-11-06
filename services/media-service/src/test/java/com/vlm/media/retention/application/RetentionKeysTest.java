package com.vlm.media.retention.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RetentionKeysTest {

  // 0x00..0x1f 32바이트의 Base64
  private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
  private static final UUID SOURCE = UUID.fromString("0192f0d2-1a2b-7c3d-8e4f-5a6b7c8d9e0f");

  @Test
  @DisplayName("[SPEC-018 R-11] 보존 키는 비밀 키와 sourceId 문자열의 HMAC-SHA256 16진 소문자 64자다")
  void keyIsHmacOfSourceId() {
    RetentionKeys keys = new RetentionKeys(new RetentionSettingsProperties(KEY));

    // 기대값은 Python hmac.new(bytes(range(32)), b"0192f0d2-...", sha256).hexdigest()로 따로 계산했다.
    assertThat(keys.keyOf(SOURCE))
        .isEqualTo("1c042f03ff043d16e08f5459c6d3e4f8cb3bfb0b936200f90592ceaaf435e75b")
        .matches("^[0-9a-f]{64}$");
  }
}
