package com.vlm.auth.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "users")
@Getter
public class User {

  // SPEC-001 R-29: 연속 5회 실패하면 15분 잠근다.
  private static final int LOCK_THRESHOLD = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private UUID publicId;

  private String email;

  private String passwordHash;

  private String nickname;

  @Enumerated(EnumType.STRING)
  private Role role;

  private int failedLoginCount;

  private @Nullable Instant lockedUntil;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected User() {}

  private User(String email, String passwordHash, String nickname, Role role) {
    this.publicId = UUID.randomUUID();
    this.email = email;
    this.passwordHash = passwordHash;
    this.nickname = nickname;
    this.role = role;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** 가입한 사용자 계정을 만든다. 역할은 USER다. */
  public static User signUp(String email, String passwordHash, String nickname) {
    return new User(email, passwordHash, nickname, Role.USER);
  }

  /** 초기 관리자 계정을 만든다(SPEC-001 R-31). */
  public static User createAdmin(String email, String passwordHash, String nickname) {
    return new User(email, passwordHash, nickname, Role.ADMIN);
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /** SPEC-001 R-29: 연속 실패가 기준에 닿으면 잠그고, 잠금이 풀린 뒤 다시 세도록 횟수를 비운다. */
  public void recordLoginFailure(Instant now) {
    failedLoginCount++;
    if (failedLoginCount >= LOCK_THRESHOLD) {
      lockedUntil = now.plus(LOCK_DURATION);
      failedLoginCount = 0;
    }
  }

  public void recordLoginSuccess() {
    failedLoginCount = 0;
    lockedUntil = null;
  }
}
