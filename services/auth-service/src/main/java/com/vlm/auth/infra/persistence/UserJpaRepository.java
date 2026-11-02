package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserJpaRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  Optional<User> findByPublicId(UUID publicId);
}
