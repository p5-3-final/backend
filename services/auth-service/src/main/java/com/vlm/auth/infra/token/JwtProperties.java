package com.vlm.auth.infra.token;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토큰 서명 설정. 형식은 docs 레포 {@code docs/contracts/auth/access-token.md}.
 *
 * @param issuer 토큰 {@code iss}
 * @param audience 접근 토큰 {@code aud}
 * @param signingKey 현재 서명 키. 개인키를 포함한 EC P-256 JWK JSON({@code kid} 포함). 비밀값이다.
 * @param previousPublicKeys 키 교체 중 JWKS에 남겨 둘 이전 공개키(JWK Set JSON). 없으면 비운다.
 */
@ConfigurationProperties("vlm.auth.jwt")
public record JwtProperties(
    String issuer, String audience, String signingKey, @Nullable String previousPublicKeys) {}
