package com.vlm.quality.golden.domain;

import java.util.Locale;
import java.util.UUID;

/** Golden Set 저장소 경로 규칙(계약 golden-manifest frameKeys, GoldenSetVersion.manifestKey). */
public final class GoldenFrames {

  private GoldenFrames() {}

  public static String manifestKey(UUID setId, int version) {
    return versionPrefix(setId, version) + "manifest.json";
  }

  /** {@code golden/<setId>/v<버전>/frames/<entryId>/<순번>.<확장자>}. 확장자는 원본 블러 사본을 따른다. */
  public static String frameKey(
      UUID setId, int version, UUID entryId, int index, String sourceKey) {
    String extension = sourceKey.substring(sourceKey.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    return versionPrefix(setId, version) + "frames/" + entryId + "/" + index + "." + extension;
  }

  private static String versionPrefix(UUID setId, int version) {
    return "golden/" + setId + "/v" + version + "/";
  }
}
