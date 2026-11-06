package com.vlm.common.id;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * RFC 4122 이름 기반 UUID 버전 5(SHA-1). JDK에는 버전 3({@link UUID#nameUUIDFromBytes})만 있다.
 *
 * <p>같은 네임스페이스·이름이면 어느 서비스가 계산해도 같은 값이라, 이벤트를 다시 받아도 같은 ID를 만들 때 쓴다(SPEC-029 R-1).
 */
public final class NameBasedUuid {

  private static final int VERSION_5 = 0x50;
  private static final int RFC_4122_VARIANT = 0x80;

  private NameBasedUuid() {}

  /**
   * 버전 5 UUID를 만든다.
   *
   * @param namespace 네임스페이스 UUID
   * @param name 이름(UTF-8로 해시한다)
   */
  public static UUID v5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      // SHA-1은 모든 JDK가 제공해야 하는 알고리즘이다(MessageDigest 문서).
      throw new IllegalStateException("SHA-1 is not available", e);
    }
    sha1.update(
        ByteBuffer.allocate(16)
            .putLong(namespace.getMostSignificantBits())
            .putLong(namespace.getLeastSignificantBits())
            .array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | VERSION_5);
    hash[8] = (byte) ((hash[8] & 0x3f) | RFC_4122_VARIANT);
    ByteBuffer bytes = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(bytes.getLong(), bytes.getLong());
  }
}
