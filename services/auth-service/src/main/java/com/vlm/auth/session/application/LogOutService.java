package com.vlm.auth.session.application;

import com.vlm.auth.session.domain.RefreshToken;
import com.vlm.auth.session.domain.RefreshTokenRepository;
import com.vlm.auth.session.domain.RefreshTokenSecret;
import com.vlm.auth.shared.domain.User;
import com.vlm.auth.shared.domain.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogOutService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final Clock clock;

  /**
   * 요청한 사용자의 재발급 토큰이면 그 로그인 계열을 폐기한다. 쿠키가 없거나 남의 토큰이어도 오류 없이 끝내서, 클라이언트는 항상 쿠키를 지우고 로그아웃 상태가 된다.
   */
  @Transactional
  public void logOut(UUID userPublicId, @Nullable String rawRefreshToken) {
    if (rawRefreshToken == null) {
      return;
    }
    Optional<User> user = userRepository.findByPublicId(userPublicId);
    Optional<RefreshToken> token =
        refreshTokenRepository.findByTokenHashForUpdate(RefreshTokenSecret.hashOf(rawRefreshToken));
    if (user.isEmpty() || token.isEmpty()) {
      return;
    }
    if (token.get().getUserId().equals(user.get().getId())) {
      refreshTokenRepository.revokeFamily(token.get().getFamilyId(), clock.instant());
    }
  }
}
