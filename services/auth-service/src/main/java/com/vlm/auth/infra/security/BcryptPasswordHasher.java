package com.vlm.auth.infra.security;

import com.vlm.auth.domain.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class BcryptPasswordHasher implements PasswordHasher {

  // strength 10: Spring Security 기본값. 로그인 한 번에 약 100ms 수준으로 대입 공격 비용을 높인다.
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String passwordHash) {
    return encoder.matches(rawPassword, passwordHash);
  }
}
