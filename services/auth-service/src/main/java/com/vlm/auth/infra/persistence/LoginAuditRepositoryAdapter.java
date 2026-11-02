package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.LoginAudit;
import com.vlm.auth.domain.LoginAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class LoginAuditRepositoryAdapter implements LoginAuditRepository {

  private final LoginAuditJpaRepository jpaRepository;

  @Override
  public LoginAudit save(LoginAudit loginAudit) {
    return jpaRepository.save(loginAudit);
  }
}
