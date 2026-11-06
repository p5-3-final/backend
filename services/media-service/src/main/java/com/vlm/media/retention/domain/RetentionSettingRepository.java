package com.vlm.media.retention.domain;

import java.util.Optional;
import java.util.UUID;

public interface RetentionSettingRepository {

  RetentionSetting save(RetentionSetting setting);

  Optional<RetentionSetting> findByOwner(UUID ownerId);
}
