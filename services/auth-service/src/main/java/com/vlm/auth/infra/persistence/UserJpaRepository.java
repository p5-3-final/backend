package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);
}
