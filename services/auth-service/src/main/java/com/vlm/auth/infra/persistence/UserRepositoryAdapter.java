package com.vlm.auth.infra.persistence;

import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.auth.domain.User;
import com.vlm.auth.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class UserRepositoryAdapter implements UserRepository {

  // V202611021200__create_auth_tables.sql의 email UNIQUE 제약에 PostgreSQL이 붙이는 이름
  private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

  private final UserJpaRepository jpaRepository;

  @Override
  public User save(User user) {
    try {
      return jpaRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      // 중복 확인 뒤 같은 이메일이 먼저 저장된 경우(동시 가입)만 중복으로 바꾸고, 다른 제약 위반은 그대로 던진다.
      if (e.getCause() instanceof ConstraintViolationException violation
          && EMAIL_UNIQUE_CONSTRAINT.equals(violation.getConstraintName())) {
        throw new BusinessException(
            AuthErrorCode.EMAIL_DUPLICATED, AuthErrorCode.EMAIL_DUPLICATED.defaultMessage(), e);
      }
      throw e;
    }
  }

  @Override
  public boolean existsByEmail(String email) {
    return jpaRepository.existsByEmail(email);
  }
}
