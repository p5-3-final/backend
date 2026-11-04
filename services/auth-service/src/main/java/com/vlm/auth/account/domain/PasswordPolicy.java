package com.vlm.auth.account.domain;

import com.vlm.auth.shared.domain.AuthErrorCode;
import com.vlm.common.error.BusinessException;

/** 가입 비밀번호 정책 (SPEC-001 R-4): 8~64자, 영문과 숫자를 모두 포함. */
public final class PasswordPolicy {

  private static final int MIN_LENGTH = 8;
  private static final int MAX_LENGTH = 64;

  private PasswordPolicy() {}

  /** 정책을 어기면 {@code AUTH_PASSWORD_POLICY} 예외를 던진다. */
  public static void validate(String rawPassword) {
    int length = rawPassword.length();
    boolean hasLetter = rawPassword.chars().anyMatch(PasswordPolicy::isAsciiLetter);
    boolean hasDigit = rawPassword.chars().anyMatch(c -> c >= '0' && c <= '9');
    if (length < MIN_LENGTH || length > MAX_LENGTH || !hasLetter || !hasDigit) {
      throw new BusinessException(AuthErrorCode.PASSWORD_POLICY);
    }
  }

  private static boolean isAsciiLetter(int c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
}
