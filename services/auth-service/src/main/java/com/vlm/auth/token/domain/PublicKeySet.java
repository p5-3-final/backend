package com.vlm.auth.token.domain;

import java.util.Map;

/** 접근 토큰 서명 검증용 공개키 목록(JWK Set). 구현은 infra 레이어에 있다. */
public interface PublicKeySet {

  /** RFC 7517 JWK Set JSON 객체. 개인키 값은 포함하지 않는다. */
  Map<String, Object> toJson();
}
