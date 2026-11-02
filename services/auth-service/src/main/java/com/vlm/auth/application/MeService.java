package com.vlm.auth.application;

import com.vlm.auth.domain.AuthErrorCode;
import com.vlm.auth.domain.User;
import com.vlm.auth.domain.UserRepository;
import com.vlm.common.error.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MeService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public User find(UUID publicId) {
    return userRepository
        .findByPublicId(publicId)
        .orElseThrow(() -> new BusinessException(AuthErrorCode.TOKEN_INVALID));
  }
}
