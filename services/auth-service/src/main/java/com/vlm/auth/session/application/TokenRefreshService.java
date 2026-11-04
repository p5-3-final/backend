package com.vlm.auth.session.application;

import com.vlm.auth.session.domain.RefreshToken;
import com.vlm.auth.session.domain.RefreshTokenRepository;
import com.vlm.auth.session.domain.RefreshTokenSecret;
import com.vlm.auth.shared.domain.AccessTokenIssuer;
import com.vlm.auth.shared.domain.AccessTokenIssuer.IssuedAccessToken;
import com.vlm.auth.shared.domain.AuthErrorCode;
import com.vlm.auth.shared.domain.User;
import com.vlm.auth.shared.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final AccessTokenIssuer accessTokenIssuer;
  private final Clock clock;

  // 재사용을 감지하면 계열 폐기를 커밋한 뒤 오류를 내야 하므로 BusinessException으로는 롤백하지 않는다.
  // 폐기를 REQUIRES_NEW로 분리하면 이 트랜잭션이 잠근 행을 기다리며 교착에 빠진다.
  // 나머지 BusinessException은 쓰기 전에 던지므로 롤백하지 않아도 남는 변경이 없다.
  @Transactional(noRollbackFor = BusinessException.class)
  public IssuedTokens refresh(@Nullable String rawRefreshToken) {
    if (rawRefreshToken == null) {
      throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
    }
    Instant now = clock.instant();
    RefreshToken current =
        refreshTokenRepository
            .findByTokenHashForUpdate(RefreshTokenSecret.hashOf(rawRefreshToken))
            .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_INVALID));
    if (current.isRevoked()) {
      throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
    }
    if (current.isUsed()) {
      // SPEC-001 R-23: 사용한 토큰이 다시 오면 탈취로 보고 계열 전체를 끊는다.
      refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
      throw new BusinessException(AuthErrorCode.REFRESH_REUSED);
    }
    if (current.isExpired(now)) {
      throw new BusinessException(AuthErrorCode.REFRESH_INVALID);
    }
    User user = userRepository.findById(current.getUserId()).orElseThrow();
    current.markUsed(now);
    RefreshTokenSecret secret = RefreshTokenSecret.generate();
    refreshTokenRepository.save(current.rotate(secret.hash(), now));
    IssuedAccessToken accessToken = accessTokenIssuer.issue(user);
    return new IssuedTokens(accessToken.value(), accessToken.expiresInSeconds(), secret.raw());
  }
}
