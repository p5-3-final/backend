package com.vlm.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/** 로그인 시도 기록 (SPEC-001 R-9). 시도한 이메일 원문은 개인정보라 남기지 않는다. */
@Entity
@Table(name = "login_audits")
@Getter
public class LoginAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private @Nullable Long userId;

  private boolean success;

  private String clientIp;

  private Instant occurredAt;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected LoginAudit() {}

  private LoginAudit(@Nullable Long userId, boolean success, String clientIp, Instant occurredAt) {
    this.userId = userId;
    this.success = success;
    this.clientIp = clientIp;
    this.occurredAt = occurredAt;
    this.createdAt = occurredAt;
    this.updatedAt = occurredAt;
  }

  /**
   * 로그인 시도를 기록한다.
   *
   * @param userId 대상 계정. 없는 이메일이면 null
   */
  public static LoginAudit of(
      @Nullable Long userId, boolean success, String clientIp, Instant occurredAt) {
    return new LoginAudit(userId, success, clientIp, occurredAt);
  }
}
