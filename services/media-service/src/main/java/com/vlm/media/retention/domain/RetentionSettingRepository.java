package com.vlm.media.retention.domain;

import java.util.Optional;
import java.util.UUID;

public interface RetentionSettingRepository {

  /** 기본 동의 값을 저장한다. 행이 없으면 만들고 있으면 바꾼다. 같은 계정의 첫 저장이 동시에 와도 행은 하나다. */
  void upsert(UUID ownerId, boolean defaultConsent);

  Optional<RetentionSetting> findByOwner(UUID ownerId);
}
