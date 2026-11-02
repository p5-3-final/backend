package com.vlm.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

@Entity
@Table(name = "users")
@Getter
public class User {

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
}
