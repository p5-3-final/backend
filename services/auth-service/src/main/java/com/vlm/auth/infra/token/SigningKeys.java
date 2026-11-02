package com.vlm.auth.infra.token;

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.vlm.auth.domain.PublicKeySet;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 설정에서 읽은 서명 키. 현재 키로 서명하고, 공개키 목록에는 현재 키와 이전 키의 공개키를 담는다. */
@Component
public class SigningKeys implements PublicKeySet {

  private final ECKey current;
  private final JWKSet publicKeys;

  public SigningKeys(JwtProperties properties) {
    try {
      this.current = ECKey.parse(properties.signingKey());
      List<JWK> keys = new ArrayList<>();
      keys.add(current.toPublicJWK());
      String previous = properties.previousPublicKeys();
      if (previous != null && !previous.isBlank()) {
        for (JWK key : JWKSet.parse(previous).getKeys()) {
          keys.add(key.toPublicJWK());
        }
      }
      this.publicKeys = new JWKSet(keys);
    } catch (ParseException e) {
      throw new IllegalStateException("vlm.auth.jwt 서명 키 설정을 읽을 수 없습니다.", e);
    }
    if (!current.isPrivate()) {
      throw new IllegalStateException("vlm.auth.jwt.signing-key에는 개인키가 있어야 합니다.");
    }
  }

  ECKey current() {
    return current;
  }

  @Override
  public Map<String, Object> toJson() {
    // toPublicJWK로 만든 키만 담았으므로 개인키 값(d)은 들어가지 않는다.
    return publicKeys.toJSONObject(true);
  }
}
