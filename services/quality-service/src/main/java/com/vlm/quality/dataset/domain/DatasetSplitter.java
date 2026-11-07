package com.vlm.quality.dataset.domain;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 영상을 개발·평가로 나눈다(SPEC-030 R-3).
 *
 * <p>해시는 {@code SHA-256(UTF-8 "<splitSeed>:<externalId>")}의 앞 8바이트를 부호 없는 정수로 보고 2^64로 나눈 값이다. 같은
 * 시드면 다시 가져와도 같은 분할이라 dev·eval이 섞이지 않는다. 평가 잡(ai evals)이 같은 식으로 다시 계산할 수 있게 식을 고정한다.
 */
public final class DatasetSplitter {

  private static final BigDecimal TWO_TO_64 = new BigDecimal(BigInteger.ONE.shiftLeft(64));

  private DatasetSplitter() {}

  /** [0, 1) 위치. */
  public static double position(long splitSeed, String externalId) {
    byte[] hash = sha256((splitSeed + ":" + externalId).getBytes(StandardCharsets.UTF_8));
    long prefix = ByteBuffer.wrap(hash, 0, Long.BYTES).getLong();
    BigInteger unsigned = new BigInteger(Long.toUnsignedString(prefix));
    return new BigDecimal(unsigned).divide(TWO_TO_64).doubleValue();
  }

  public static Split split(long splitSeed, String externalId, double evalRatio) {
    return position(splitSeed, externalId) < evalRatio ? Split.EVAL : Split.DEV;
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256은 모든 JDK가 제공해야 하는 알고리즘이다(MessageDigest 문서).
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
