package com.vlm.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 접근 토큰 검증 설정. 값은 docs 레포 {@code docs/contracts/auth/access-token.md}를 따른다.
 *
 * @param jwksUri auth-service 공개키 목록 주소(클러스터 내부)
 * @param issuer 받아들일 {@code iss}
 * @param audience 받아들일 {@code aud}
 */
@ConfigurationProperties("vlm.gateway.jwt")
public record GatewayJwtProperties(String jwksUri, String issuer, String audience) {}
