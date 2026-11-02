package com.vlm.auth.application;

import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.auth.domain.PasswordHasher;
import com.vlm.auth.domain.PasswordPolicy;
import com.vlm.auth.domain.User;
import com.vlm.auth.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SignUpService {

  private final UserRepository userRepository;
  private final PasswordHasher passwordHasher;

  @Transactional
  public User signUp(SignUpCommand command) {
    // 이메일은 대소문자를 구분하지 않으므로 소문자로 저장해 유니크 제약이 그대로 중복을 막게 한다.
    String email = command.email().trim().toLowerCase(Locale.ROOT);
    PasswordPolicy.validate(command.password());
    if (userRepository.existsByEmail(email)) {
      throw new BusinessException(AuthErrorCode.EMAIL_DUPLICATED);
    }
    return userRepository.save(
        User.signUp(email, passwordHasher.hash(command.password()), command.nickname()));
  }
}
