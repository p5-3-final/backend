package com.vlm.auth.api;

import com.vlm.auth.domain.PublicKeySet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 클러스터 내부 전용. api-gateway가 접근 토큰 서명을 검증할 때 읽는다(SPEC-001 R-11). */
@RestController
@RequiredArgsConstructor
class JwksController {

  private final PublicKeySet publicKeySet;

  @GetMapping("/.well-known/jwks.json")
  Map<String, Object> jwks() {
    return publicKeySet.toJson();
  }
}
