package com.vlm.common.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NameBasedUuidTest {

  // RFC 4122 부록의 DNS 네임스페이스
  private static final UUID DNS = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");

  @Test
  @DisplayName("[SPEC-029 R-1] uuid5(DNS, www.example.com)이 RFC 4122 시험 벡터와 같다")
  void matchesRfcVector() {
    assertThat(NameBasedUuid.v5(DNS, "www.example.com"))
        .isEqualTo(UUID.fromString("2ed6657d-e927-568b-95e1-2665a8aea6a2"));
  }

  @Test
  @DisplayName("[SPEC-029 R-1] 같은 네임스페이스·이름은 항상 같은 버전 5 UUID다")
  void isDeterministicVersion5() {
    UUID first = NameBasedUuid.v5(DNS, "event-1");

    assertThat(NameBasedUuid.v5(DNS, "event-1")).isEqualTo(first);
    assertThat(first.version()).isEqualTo(5);
    assertThat(first.variant()).isEqualTo(2);
    assertThat(NameBasedUuid.v5(DNS, "event-2")).isNotEqualTo(first);
  }
}
