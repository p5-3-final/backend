package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshToken, Long> {}
