package com.vlm.auth.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vlm.common.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        "secret12",
        "abc12345!@#",
        "A1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  @DisplayName("[SPEC-001 R-4] 8~64자이고 영문과 숫자를 모두 포함하면 통과한다")
  void accepts(String password) {
    assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(
      strings = {
        "secret1",
        "abcdefgh",
        "12345678",
        "!@#$%^&*",
        "A1aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  @DisplayName("[SPEC-001 R-4] 길이가 8~64자가 아니거나 영문·숫자 중 하나가 없으면 AUTH_PASSWORD_POLICY")
  void rejects(String password) {
    assertThatThrownBy(() -> PasswordPolicy.validate(password))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode().code())
        .isEqualTo("AUTH_PASSWORD_POLICY");
  }
}
