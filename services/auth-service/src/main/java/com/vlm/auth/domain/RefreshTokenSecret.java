package com.vlm.auth.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 재발급 토큰의 원문과 해시. 원문은 쿠키로만 내보내고 DB에는 해시만 저장한다(SPEC-001 NFR-2).
 *
 * @param raw 클라이언트에 주는 원문(256비트 난수, base64url)
 * @param hash 원문의 SHA-256 hex
 */
public record RefreshTokenSecret(String raw, String hash) {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  public static RefreshTokenSecret generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new RefreshTokenSecret(raw, hashOf(raw));
  }

  /** 제출된 원문을 저장된 해시와 비교할 수 있도록 같은 방식으로 해시한다. */
  public static String hashOf(String raw) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // 모든 JDK가 SHA-256을 제공해야 하므로 여기 오면 실행 환경이 잘못된 것이다.
      throw new IllegalStateException(e);
    }
  }
}
