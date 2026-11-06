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
  public void upsert(UUID ownerId, boolean defaultConsent) {
    jpaRepository.upsert(ownerId, defaultConsent);
  }

  @Override
  public Optional<RetentionSetting> findByOwner(UUID ownerId) {
    return jpaRepository.findByOwnerId(ownerId);
  }
}
