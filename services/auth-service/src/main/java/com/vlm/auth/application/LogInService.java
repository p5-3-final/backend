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
import java.time.Instant;
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

  // 실패 횟수와 잠금은 401·423 응답과 함께 저장돼야 하므로 BusinessException으로는 롤백하지 않는다.
  // 이 메서드는 그 외의 쓰기를 모두 성공 경로에서만 한다.
  @Transactional(noRollbackFor = BusinessException.class)
  public IssuedTokens logIn(LogInCommand command) {
    String email = command.email().trim().toLowerCase(Locale.ROOT);
    Instant now = clock.instant();
    Optional<User> found = userRepository.findByEmailForUpdate(email);
    if (found.isEmpty()) {
      // 계정이 없어도 해시 비교를 한 번 해서, 응답 시간으로 가입 여부가 드러나지 않게 한다.
      passwordHasher.matches(command.password(), DUMMY_PASSWORD_HASH);
      loginAuditRecorder.record(null, false, command.clientIp());
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    User user = found.get();
    Long userId = Objects.requireNonNull(user.getId());
    if (user.isLocked(now)) {
      loginAuditRecorder.record(userId, false, command.clientIp());
      throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
    }
    if (!passwordHasher.matches(command.password(), user.getPasswordHash())) {
      user.recordLoginFailure(now);
      loginAuditRecorder.record(userId, false, command.clientIp());
      throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
    }
    user.recordLoginSuccess();
    loginAuditRecorder.record(userId, true, command.clientIp());
    IssuedAccessToken accessToken = accessTokenIssuer.issue(user);
    RefreshTokenSecret secret = RefreshTokenSecret.generate();
    refreshTokenRepository.save(RefreshToken.startFamily(userId, secret.hash(), now));
    return new IssuedTokens(accessToken.value(), accessToken.expiresInSeconds(), secret.raw());
  }
}
