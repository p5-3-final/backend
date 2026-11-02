package com.vlm.gateway.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 계약 {@code access-token.md} 형식의 테스트 토큰을 만든다. 키는 실행할 때마다 새로 생성해 저장소에 개인키를 두지 않는다.
 *
 * <p>게이트웨이는 {@link #jwks()}를 WireMock으로 제공받아 검증한다.
 */
public final class TestTokens {

  public static final String ISSUER = "vlm-auth-test";
  public static final String AUDIENCE = "vlm-api";
  public static final String TICKET_AUDIENCE = "vlm-sse";
  public static final ECKey SIGNING_KEY = generate("gateway-test");

  /** 키 교체 후 새로 쓰는 키. 처음에는 JWKS에 없다. */
  public static final ECKey NEXT_KEY = generate("gateway-next");

  /** JWKS에 없는 키. 서명 위조 테스트에 쓴다. */
  public static final ECKey UNKNOWN_KEY = generate("unknown");

  /** JWKS의 키와 kid만 같고 실제로는 다른 키. 서명 불일치 테스트에 쓴다. */
  public static final ECKey FORGED_KEY = generate("gateway-test");

  private TestTokens() {}

  public static String jwks() {
    return jwks(SIGNING_KEY);
  }

  public static String jwks(ECKey... keys) {
    return new JWKSet(
            java.util.Arrays.stream(keys)
                .map(k -> (com.nimbusds.jose.jwk.JWK) k.toPublicJWK())
                .toList())
        .toString();
  }

  /** 유효한 접근 토큰. */
  public static String valid(String subject, String... roles) {
    return sign(SIGNING_KEY, claims(subject, roles).build());
  }

  /** 유효한 클레임에서 일부를 바꾼 토큰. */
  public static String with(UnaryOperator<JWTClaimsSet.Builder> customizer) {
    return sign(
        SIGNING_KEY, customizer.apply(claims(UUID.randomUUID().toString(), "USER")).build());
  }

  /** 계약 "알림 연결 티켓" 형식의 유효한 티켓(aud vlm-sse, 30초). */
  public static String ticket(String subject, String... roles) {
    return ticketWith(subject, b -> b, roles);
  }

  /** 유효한 티켓 클레임에서 일부를 바꾼 티켓. */
  public static String ticketWith(
      String subject, UnaryOperator<JWTClaimsSet.Builder> customizer, String... roles) {
    Instant now = Instant.now();
    JWTClaimsSet.Builder builder =
        claims(subject, roles)
            .audience(TICKET_AUDIENCE)
            .expirationTime(toDate(now.plusSeconds(30)));
    return sign(SIGNING_KEY, customizer.apply(builder).build());
  }

  /** 다른 키로 서명한 티켓. */
  public static String ticketSignedWith(ECKey key) {
    return sign(
        key, claims(UUID.randomUUID().toString(), "USER").audience(TICKET_AUDIENCE).build());
  }

  /** ES256이 아닌 HS256으로 서명한 토큰. 알고리즘 제한 테스트에 쓴다. */
  public static String hmacSigned() {
    try {
      JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build();
      SignedJWT jwt = new SignedJWT(header, claims(UUID.randomUUID().toString(), "USER").build());
      jwt.sign(new MACSigner(new byte[32]));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public static String signedWith(ECKey key) {
    return sign(key, claims(UUID.randomUUID().toString(), "USER").build());
  }

  // Nimbus 빌더가 Date만 받으므로 테스트 지원 코드에서만 Date로 바꾼다.
  @SuppressWarnings("JavaUtilDate")
  public static Date toDate(Instant instant) {
    return Date.from(instant);
  }

  private static JWTClaimsSet.Builder claims(String subject, String... roles) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .subject(subject)
        .claim("roles", List.of(roles))
        .issuer(ISSUER)
        .audience(AUDIENCE)
        .issueTime(toDate(now))
        .expirationTime(toDate(now.plusSeconds(900)))
        .jwtID(UUID.randomUUID().toString());
  }

  private static String sign(ECKey key, JWTClaimsSet claims) {
    try {
      JWSHeader header =
          new JWSHeader.Builder(JWSAlgorithm.ES256)
              .keyID(key.getKeyID())
              .type(JOSEObjectType.JWT)
              .build();
      SignedJWT jwt = new SignedJWT(header, claims);
      jwt.sign(new ECDSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static ECKey generate(String keyId) {
    try {
      return new ECKeyGenerator(Curve.P_256)
          .keyID(keyId)
          .keyUse(KeyUse.SIGNATURE)
          .algorithm(JWSAlgorithm.ES256)
          .generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
