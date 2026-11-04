package com.vlm.auth.session.infra.persistence;

import com.vlm.auth.session.domain.LoginAudit;
import com.vlm.auth.session.domain.LoginAuditRepository;
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
