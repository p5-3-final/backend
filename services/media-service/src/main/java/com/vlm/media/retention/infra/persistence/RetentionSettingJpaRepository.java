package com.vlm.media.retention.infra.persistence;

import com.vlm.media.retention.domain.RetentionSetting;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RetentionSettingJpaRepository extends JpaRepository<RetentionSetting, Long> {

  Optional<RetentionSetting> findByOwnerId(UUID ownerId);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO retention_settings (owner_id, default_consent)
          VALUES (:ownerId, :defaultConsent)
          ON CONFLICT (owner_id)
          DO UPDATE SET default_consent = EXCLUDED.default_consent, updated_at = now()
          """,
      nativeQuery = true)
  void upsert(@Param("ownerId") UUID ownerId, @Param("defaultConsent") boolean defaultConsent);
}
