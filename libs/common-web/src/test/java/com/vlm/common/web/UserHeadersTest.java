package com.vlm.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserHeadersTest {

  @Test
  @DisplayName("사용자 ID 헤더를 UUID로 읽는다")
  void parsesUserId() {
    UUID id = UUID.randomUUID();

    assertThat(UserHeaders.userId(id.toString())).isEqualTo(id);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = " ")
  @DisplayName("사용자 ID 헤더가 없으면 401 AUTH_TOKEN_MISSING이다")
  void missingUserIdIsTokenMissing(String value) {
    assertThatThrownBy(() -> UserHeaders.userId(value))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode().code()).isEqualTo("AUTH_TOKEN_MISSING");
              assertThat(e.getErrorCode().status()).isEqualTo(401);
            });
  }

  @Test
  @DisplayName("사용자 ID 헤더가 UUID가 아니면 401 AUTH_TOKEN_INVALID다")
  void malformedUserIdIsTokenInvalid() {
    assertThatThrownBy(() -> UserHeaders.userId("not-a-uuid"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode().code()).isEqualTo("AUTH_TOKEN_INVALID");
              assertThat(e.getErrorCode().status()).isEqualTo(401);
            });
  }

  @Test
  @DisplayName("쉼표로 이은 역할 중에 ADMIN이 있으면 관리자다")
  void adminRole() {
    assertThat(UserHeaders.isAdmin("USER, ADMIN")).isTrue();
    assertThat(UserHeaders.isAdmin("USER")).isFalse();
    assertThat(UserHeaders.isAdmin("ADMINISTRATOR")).isFalse();
    assertThat(UserHeaders.isAdmin(null)).isFalse();
  }
}
