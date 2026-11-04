package com.vlm.auth.token.infra;

import com.vlm.auth.shared.domain.AccessTokenIssuer;
import com.vlm.auth.shared.domain.User;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 계약 {@code access-token.md}의 접근 토큰을 발급한다. */
@Component
@RequiredArgsConstructor
class JwtAccessTokenIssuer implements AccessTokenIssuer {

  static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);

  private final UserTokenSigner signer;
  private final JwtProperties properties;

  @Override
  public IssuedAccessToken issue(User user) {
    return new IssuedAccessToken(
        signer.sign(user, properties.audience(), TIME_TO_LIVE), TIME_TO_LIVE.toSeconds());
  }
}
