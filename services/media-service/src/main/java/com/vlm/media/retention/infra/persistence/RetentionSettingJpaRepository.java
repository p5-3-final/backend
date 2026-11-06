package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetentionSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface RetentionSettingJpaRepository extends JpaRepository<RetentionSetting, Long> {

  Optional<RetentionSetting> findByOwnerId(UUID ownerId);
}
