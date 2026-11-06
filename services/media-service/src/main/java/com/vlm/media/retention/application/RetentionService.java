package com.vlm.media.retention.application;

import com.vlm.media.retention.domain.RetentionSetting;
import com.vlm.media.retention.domain.RetentionSettingRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RetentionService {

  private final RetentionSettingRepository settingRepository;

  /** 계정의 기본 동의 값. 바꾼 적이 없으면 false다(SPEC-018 R-1, ADR-0011 "기본값은 보존하지 않음"). */
  @Transactional(readOnly = true)
  public boolean defaultConsent(UUID ownerId) {
    return settingRepository
        .findByOwner(ownerId)
        .map(RetentionSetting::isDefaultConsent)
        .orElse(false);
  }
}
