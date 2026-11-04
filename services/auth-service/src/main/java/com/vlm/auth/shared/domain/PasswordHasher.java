package com.vlm.auth.shared.domain;

/** 비밀번호 단방향 해시 (SPEC-001 R-6). 구현은 infra 레이어에 있다. */
public interface PasswordHasher {

  String hash(String rawPassword);

  boolean matches(String rawPassword, String passwordHash);
}
