package com.vlm.auth.domain;

import java.util.Optional;
import java.util.UUID;

/** 사용자 저장소. 구현은 infra 레이어에 있다. */
public interface UserRepository {

  /**
   * 저장하고 즉시 DB에 반영한다. 유니크 제약 위반을 이 호출에서 감지하기 위해서다.
   *
   * @throws com.vlm.common.error.BusinessException 같은 이메일이 이미 저장되어 있으면 {@code
   *     AUTH_EMAIL_DUPLICATED}
   */
  User save(User user);

  boolean existsByEmail(String email);

  boolean existsByRole(Role role);

  /** 같은 계정의 로그인 실패 횟수가 동시 요청으로 덮어써지지 않도록 행을 잠그고 조회한다. */
  Optional<User> findByEmailForUpdate(String email);

  Optional<User> findByPublicId(UUID publicId);

  Optional<User> findById(Long id);
}
