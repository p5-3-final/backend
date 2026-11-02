package com.vlm.auth.application;

import com.vlm.auth.domain.AccessTokenIssuer;
import com.vlm.auth.domain.AccessTokenIssuer.IssuedAccessToken;
import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.auth.domain.PasswordHasher;
import com.vlm.auth.domain.RefreshToken;
import com.vlm.auth.domain.RefreshTokenRepository;
import com.vlm.auth.domain.RefreshTokenSecret;
import com.vlm.auth.domain.User;
import com.vlm.auth.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogInService {

  // 어떤 비밀번호와도 일치하지 않는 BCrypt(strength 10) 해시. 비교 시간만 맞추는 용도다.
  private static final String DUMMY_PASSWORD_HASH =
      "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z3J0Y6pE8z1u1kQ5r6Wl1hW.";

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordHasher passwordHasher;
  private final AccessTokenIssuer accessTokenIssuer;
  private final LoginAuditRecorder loginAuditRecorder;
  private final Clock clock;

  @Transactional
  public LogInResult logIn(LogInCommand command) {
    String email = command.email().trim().toLowerCase(Locale.ROOT);
    Optional<User> found = userRepository.findByEmail(email);
    if (found.isEmpty()) {
      // 계정이 없어도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부가 드러나지 않게 한다.
      passwordHasher.matches(command.password(), DUMMY_PASSWORD_HASH);
      loginAuditRecorder.record(null, false, command.clientIp());
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    User user = found.get();
    Long userId = Objects.requireNonNull(user.getId());
    if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
      loginAuditRecorder.record(userId, false, command.clientIp());
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    loginAuditRecorder.record(userId, true, command.clientIp());
    IssuedAccessToken accessToken = accessTokenIssuer.issue(user);
    RefreshTokenSecret secret = RefreshTokenSecret.generate();
    refreshTokenRepository.save(RefreshToken.startFamily(userId, secret.hash(), clock.instant()));
    return new LogInResult(accessToken.value(), accessToken.expiresInSeconds(), secret.raw());
  }
}
