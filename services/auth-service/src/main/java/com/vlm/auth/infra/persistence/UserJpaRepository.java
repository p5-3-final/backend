package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.Role;
import com.vlm.auth.domain.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface UserJpaRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<User> findWithLockByEmail(String email);

  Optional<User> findByPublicId(UUID publicId);
}
