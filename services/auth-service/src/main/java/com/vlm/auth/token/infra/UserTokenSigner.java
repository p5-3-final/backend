package com.vlm.auth.token.infra;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.vlm.auth.shared.domain.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/** 계약 {@code access-token.md}의 공통 클레임으로 사용자 JWT를 ES256 서명한다. 접근 토큰과 알림 티켓은 aud와 유효기간만 다르다. */
@Component
class UserTokenSigner {

  private final JwtEncoder encoder;
  private final String keyId;
  private final String issuer;
  private final Clock clock;

  UserTokenSigner(SigningKeys signingKeys, JwtProperties properties, Clock clock) {
    this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKeys.current())));
    this.keyId = signingKeys.current().getKeyID();
    this.issuer = properties.issuer();
    this.clock = clock;
  }

  String sign(User user, String audience, Duration timeToLive) {
    Instant now = clock.instant();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(keyId).type("JWT").build();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getPublicId().toString())
            .claim("roles", List.of(user.getRole().name()))
            .issuer(issuer)
            .audience(List.of(audience))
            .issuedAt(now)
            .expiresAt(now.plus(timeToLive))
            .id(UUID.randomUUID().toString())
            .build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
