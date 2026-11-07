package com.vlm.quality.dataset.domain;

import java.util.Locale;

/** 개발·평가 분할(R-3). Golden Set 이름 접미사와 계약 split 값이 소문자 이름이다. */
public enum Split {
  DEV,
  EVAL;

  public String wire() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Split fromWire(String value) {
    return valueOf(value.toUpperCase(Locale.ROOT));
  }
}
