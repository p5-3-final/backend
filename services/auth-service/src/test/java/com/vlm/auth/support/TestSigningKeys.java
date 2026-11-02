package com.vlm.auth.support;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 테스트 실행 때마다 새 EC P-256 키를 만들어 서명 키 설정에 넣는다. 저장소에 테스트용 개인키를 커밋하지 않기 위해서다.
 *
 * <p>현재 키와, 키 교체 중인 상황을 흉내 내는 이전 공개키를 함께 설정한다.
 */
public final class TestSigningKeys
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  public static final String ISSUER = "vlm-auth-test";
  public static final ECKey CURRENT = generate("test-current");
  public static final ECKey PREVIOUS = generate("test-previous");

  @Override
  public void initialize(ConfigurableApplicationContext context) {
    TestPropertyValues.of(
            "vlm.auth.jwt.issuer=" + ISSUER,
            "vlm.auth.jwt.signing-key=" + CURRENT.toJSONString(),
            "vlm.auth.jwt.previous-public-keys=" + new JWKSet(PREVIOUS.toPublicJWK()))
        .applyTo(context);
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
