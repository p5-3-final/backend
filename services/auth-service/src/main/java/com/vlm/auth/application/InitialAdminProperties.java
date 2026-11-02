package com.vlm.auth.application;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 초기 관리자 계정 설정(SPEC-001 R-31). 운영에서는 Secrets Manager로 주입한다.
 *
 * @param email 관리자 이메일. 비우면 만들지 않는다.
 * @param password 관리자 비밀번호. 비밀값이다.
 */
@ConfigurationProperties("vlm.auth.admin")
public record InitialAdminProperties(@Nullable String email, @Nullable String password) {}
