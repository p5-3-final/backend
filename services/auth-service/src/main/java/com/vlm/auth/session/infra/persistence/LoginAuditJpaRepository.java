package com.vlm.auth.session.infra.persistence;

import com.vlm.auth.session.domain.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoginAuditJpaRepository extends JpaRepository<LoginAudit, Long> {}
