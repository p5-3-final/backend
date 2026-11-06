package com.vlm.media.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/** 계정의 학습용 보존 기본 동의 값(SPEC-018 R-1, R-2). 행이 없는 계정은 기본값 false다. */
@Entity
@Table(name = "retention_settings")
@Getter
public class RetentionSetting {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private UUID ownerId;

  private boolean defaultConsent;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected RetentionSetting() {}

  private RetentionSetting(UUID ownerId, boolean defaultConsent, Instant now) {
    this.ownerId = ownerId;
    this.defaultConsent = defaultConsent;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static RetentionSetting create(UUID ownerId, boolean defaultConsent, Instant now) {
    return new RetentionSetting(ownerId, defaultConsent, now);
  }

  public void changeDefaultConsent(boolean defaultConsent) {
    this.defaultConsent = defaultConsent;
  }
}
