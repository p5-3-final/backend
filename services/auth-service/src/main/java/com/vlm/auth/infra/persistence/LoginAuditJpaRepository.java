package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

interface LoginAuditJpaRepository extends JpaRepository<LoginAudit, Long> {}
