package com.vlm.quality.review.domain;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * 판정이 확정된 이벤트의 무작위 표본(SPEC-029 R-2).
 *
 * <p>난수 대신 eventId 해시를 쓴다. 같은 이벤트를 다시 받아도 결과가 같아 재처리로 표본이 늘거나 줄지 않는다.
 */
public final class ReviewSampling {

  // 2^64. 해시 앞 8바이트를 부호 없는 정수로 보고 이 값으로 나눠 [0, 1)로 옮긴다.
  private static final double TWO_POW_64 = 0x1p64;

  private ReviewSampling() {}

  /** {@code SHA-256(eventId 소문자 문자열)} 앞 8바이트를 [0, 1)로 옮긴 값. */
  public static double hash(UUID eventId) {
    MessageDigest sha256;
    try {
      sha256 = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 모든 JDK가 제공해야 하는 알고리즘이다(MessageDigest 문서).
      throw new IllegalStateException("SHA-256 is not available", e);
    }
    byte[] digest = sha256.digest(eventId.toString().getBytes(StandardCharsets.UTF_8));
    long head = ByteBuffer.wrap(digest, 0, Long.BYTES).getLong();
    double unsigned = head >= 0 ? head : head + TWO_POW_64;
    return Math.min(unsigned / TWO_POW_64, Math.nextDown(1.0));
  }

  /** 해시가 비율 안이면 표본이다. 비율 0이면 아무것도 고르지 않고 1이면 모두 고른다. */
  public static boolean sampled(UUID eventId, double rate) {
    return hash(eventId) < rate;
  }
}
