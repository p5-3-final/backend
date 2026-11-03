package com.vlm.media.infra.storage;

import java.nio.charset.StandardCharsets;

/** 서명 주소의 {@code response-content-disposition} 값. */
final class ContentDispositions {

  // RFC 5987 attr-char 중 영숫자를 뺀 나머지. 이 밖의 바이트는 퍼센트 인코딩한다.
  private static final String ATTR_CHAR_SYMBOLS = "!#$&+-.^_`|~";

  private ContentDispositions() {}

  /**
   * 원래 파일 이름으로 내려받게 하는 값(SPEC-006 R-1). 한글 이름은 {@code filename*}로 전하고, 이를 모르는 클라이언트를 위해 ASCII로 바꾼
   * {@code filename}도 함께 준다. 헤더를 깨뜨릴 수 있는 따옴표·역슬래시·제어 문자는 대체 이름에 넣지 않는다.
   */
  static String attachment(String name) {
    return "attachment; filename=\""
        + asciiFallback(name)
        + "\"; filename*=UTF-8''"
        + rfc5987(name);
  }

  private static String asciiFallback(String fileName) {
    StringBuilder out = new StringBuilder();
    fileName
        .codePoints()
        .forEach(c -> out.append(c >= 0x20 && c < 0x7f && c != '"' && c != '\\' ? (char) c : '_'));
    return out.toString();
  }

  private static String rfc5987(String fileName) {
    StringBuilder out = new StringBuilder();
    for (byte b : fileName.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xff;
      if (isAlphaNumeric(c) || ATTR_CHAR_SYMBOLS.indexOf(c) >= 0) {
        out.append((char) c);
      } else {
        out.append('%')
            .append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
            .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
      }
    }
    return out.toString();
  }

  private static boolean isAlphaNumeric(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }
}
