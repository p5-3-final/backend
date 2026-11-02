package com.vlm.auth.infra.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.vlm.auth.domain.AccessTokenIssuer;
import com.vlm.auth.domain.User;
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

/** 계약 {@code access-token.md}에 맞춰 ES256 접근 토큰을 서명한다. */
@Component
class JwtAccessTokenIssuer implements AccessTokenIssuer {

  static final Duration TIME_TO_LIVE = Duration.ofMinutes(15);

  private final JwtEncoder encoder;
  private final String keyId;
  private final JwtProperties properties;
  private final Clock clock;

  JwtAccessTokenIssuer(SigningKeys signingKeys, JwtProperties properties, Clock clock) {
    this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKeys.current())));
    this.keyId = signingKeys.current().getKeyID();
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public IssuedAccessToken issue(User user) {
    Instant now = clock.instant();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(keyId).type("JWT").build();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(user.getPublicId().toString())
            .claim("roles", List.of(user.getRole().name()))
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .issuedAt(now)
            .expiresAt(now.plus(TIME_TO_LIVE))
            .id(UUID.randomUUID().toString())
            .build();
    String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedAccessToken(token, TIME_TO_LIVE.toSeconds());
  }
}
