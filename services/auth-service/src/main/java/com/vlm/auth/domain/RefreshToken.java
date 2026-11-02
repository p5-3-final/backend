package com.vlm.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/** 재발급 토큰. 원문 대신 해시를 저장하고, 같은 로그인에서 회전된 토큰은 familyId로 묶는다. */
@Entity
@Table(name = "refresh_tokens")
@Getter
public class RefreshToken {

  /** 재발급 토큰 유효기간(SPEC-001 R-7). */
  public static final Duration TIME_TO_LIVE = Duration.ofDays(14);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private Long userId;

  private UUID familyId;

  // SHA-256 hex(64자)
  @Column(length = 64)
  private String tokenHash;

  private Instant expiresAt;

  private @Nullable Instant usedAt;

  private @Nullable Instant revokedAt;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected RefreshToken() {}

  private RefreshToken(Long userId, UUID familyId, String tokenHash, Instant now) {
    this.userId = userId;
    this.familyId = familyId;
    this.tokenHash = tokenHash;
    this.expiresAt = now.plus(TIME_TO_LIVE);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 로그인할 때 새 로그인 계열의 첫 토큰을 만든다. */
  public static RefreshToken startFamily(Long userId, String tokenHash, Instant now) {
    return new RefreshToken(userId, UUID.randomUUID(), tokenHash, now);
  }

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  /** 재발급에 쓰였다고 표시한다. 같은 토큰이 다시 오면 재사용으로 본다(SPEC-001 R-23). */
  public void markUsed(Instant now) {
    this.usedAt = now;
  }

  /** 같은 로그인 계열로 새 토큰을 만든다(회전). */
  public RefreshToken rotate(String newTokenHash, Instant now) {
    return new RefreshToken(userId, familyId, newTokenHash, now);
  }
}
