package com.vlm.auth.tools;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * 로컬 실행용 서명 키를 만든다({@code ./gradlew :services:auth-service:generateSigningKey}).
 *
 * <p>출력은 개인키를 포함한 EC P-256 JWK JSON이고 {@code AUTH_JWT_SIGNING_KEY}에 넣는다. 운영 키는 Secrets Manager에서
 * 관리한다.
 */
public final class SigningKeyGenerator {

  private SigningKeyGenerator() {}

  public static void main(String[] args) throws JOSEException {
    // kid는 키 교체 때 구분할 수 있도록 만든 날짜를 넣는다(access-token.md 키 교체 규칙).
    String keyId = "local-" + LocalDate.now(ZoneOffset.UTC);
    ECKey key =
        new ECKeyGenerator(Curve.P_256)
            .keyID(keyId)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.ES256)
            .generate();
    System.out.println(key.toJSONString());
  }
}
