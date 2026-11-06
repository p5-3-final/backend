package com.vlm.quality.alert.domain;

import java.util.Arrays;
import java.util.Locale;

/** alert.raised의 status. pending 다음에 confirmed나 dismissed 중 하나가 온다. */
public enum AlertStatus {
  PENDING,
  CONFIRMED,
  DISMISSED;

  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** 계약 enum 값을 읽는다. 스키마 검증을 통과한 값만 들어온다. */
  public static AlertStatus fromWire(String value) {
    return Arrays.stream(values())
        .filter(s -> s.wire().equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown alert status " + value));
  }
}
