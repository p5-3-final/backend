package com.vlm.auth.account.application;

import com.vlm.auth.account.domain.PasswordPolicy;
import com.vlm.auth.shared.domain.PasswordHasher;
import com.vlm.auth.shared.domain.Role;
import com.vlm.auth.shared.domain.User;
import com.vlm.auth.shared.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 시작할 때 관리자가 하나도 없으면 설정값으로 ADMIN 계정을 만든다(SPEC-001 R-31). */
@Component
@RequiredArgsConstructor
public class InitialAdminCreator implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(InitialAdminCreator.class);

  // 가입 닉네임 규칙(2~20자, 한글·영문·숫자)에 맞는 값
  private static final String NICKNAME = "관리자";

  private final InitialAdminProperties properties;
  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;

  // 여러 파드가 동시에 시작해 둘 다 만들려 하면 이메일 유니크 제약으로 한쪽이 실패한다. 그 파드는 재시작한 뒤 관리자가 있으므로 건너뛴다.
  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.existsByRole(Role.ADMIN)) {
      return;
    }
    String email = properties.email();
    String password = properties.password();
    if (email == null || email.isBlank() || password == null || password.isBlank()) {
      log.warn("Initial admin not created: AUTH_ADMIN_EMAIL or AUTH_ADMIN_PASSWORD is not set");
      return;
    }
    validate(password);
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    if (userRepository.existsByEmail(normalizedEmail)) {
      // 일반 계정을 몰래 관리자로 올리지 않는다. 운영자가 다른 이메일을 지정해야 한다.
      log.warn("Initial admin not created: AUTH_ADMIN_EMAIL is already used by another account");
      return;
    }
    userRepository.save(User.createAdmin(normalizedEmail, passwordHasher.hash(password), NICKNAME));
    log.info("Initial admin account created");
  }

  // 약한 관리자 비밀번호로 운영되지 않도록 시작을 멈춘다.
  private static void validate(String password) {
    try {
      PasswordPolicy.validate(password);
    } catch (BusinessException e) {
      throw new IllegalStateException("AUTH_ADMIN_PASSWORD violates the password policy", e);
    }
  }
}
