package com.vlm.gateway.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 접근 토큰 검증 설정. 값은 docs 레포 {@code service-contracts/auth/access-token.md}를 따른다.
 *
 * @param jwksUri auth-service 공개키 목록 주소(클러스터 내부)
 * @param issuer 받아들일 {@code iss}
 * @param audience 접근 토큰으로 받아들일 {@code aud}
 * @param ticketAudience 알림 연결 티켓으로 받아들일 {@code aud}
 * @param jwksCacheTtl 공개키 목록 캐시 갱신 주기. 계약상 5분 이내
 * @param jwksRefetchCooldown 모르는 kid 때문에 다시 받는 최소 간격
 */
@ConfigurationProperties("vlm.gateway.jwt")
public record GatewayJwtProperties(
    String jwksUri,
    String issuer,
    String audience,
    String ticketAudience,
    @DefaultValue("5m") Duration jwksCacheTtl,
    @DefaultValue("10s") Duration jwksRefetchCooldown) {}
