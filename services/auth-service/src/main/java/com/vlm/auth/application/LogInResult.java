package com.vlm.auth.application;

/**
 * 로그인 결과.
 *
 * @param accessToken 접근 토큰(JWT)
 * @param expiresIn 접근 토큰 만료까지 남은 초
 * @param refreshToken 재발급 토큰 원문. 쿠키로만 내보낸다.
 */
public record LogInResult(String accessToken, long expiresIn, String refreshToken) {}
