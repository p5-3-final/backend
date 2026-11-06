package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetentionSetting;
import com.vlm.media.retention.domain.RetentionSettingRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class RetentionSettingRepositoryAdapter implements RetentionSettingRepository {

  private final RetentionSettingJpaRepository jpaRepository;

  @Override
  public RetentionSetting save(RetentionSetting setting) {
    return jpaRepository.save(setting);
  }

  @Override
  public Optional<RetentionSetting> findByOwner(UUID ownerId) {
    return jpaRepository.findByOwnerId(ownerId);
  }
}
