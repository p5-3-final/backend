package com.vlm.auth.session.domain;

/** 로그인 기록 저장소. 구현은 infra 레이어에 있다. */
public interface LoginAuditRepository {

  LoginAudit save(LoginAudit loginAudit);
}
