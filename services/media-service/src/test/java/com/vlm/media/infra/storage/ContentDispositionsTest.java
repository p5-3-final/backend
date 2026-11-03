package com.vlm.media.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContentDispositionsTest {

  @Test
  @DisplayName("[SPEC-006 R-1] ASCII 이름은 그대로 두고 RFC 5987 이름도 함께 준다")
  void asciiName() {
    assertThat(ContentDispositions.attachment("parking-0930.mp4"))
        .isEqualTo("attachment; filename=\"parking-0930.mp4\"; filename*=UTF-8''parking-0930.mp4");
  }

  @Test
  @DisplayName("[SPEC-006 R-1] 한글과 따옴표는 대체 이름에서 _로 바꾸고 RFC 5987 이름은 퍼센트 인코딩한다")
  void nonAsciiAndQuotes() {
    assertThat(ContentDispositions.attachment("주차 \"1\".mp4"))
        .isEqualTo(
            "attachment; filename=\"__ _1_.mp4\"; "
                + "filename*=UTF-8''%EC%A3%BC%EC%B0%A8%20%221%22.mp4");
  }
}
