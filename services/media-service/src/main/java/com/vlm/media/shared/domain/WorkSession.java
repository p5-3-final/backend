package com.vlm.media.shared.domain;

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

/** 작업 세션. 영상 여러 개를 작업 하나(작업 전·중·후)로 묶는다(SPEC-017). */
@Entity
@Table(name = "work_sessions")
@Getter
public class WorkSession {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private UUID publicId;

  private UUID ownerId;

  private String name;

  private @Nullable String site;

  private @Nullable String area;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected WorkSession() {}

  private WorkSession(
      UUID ownerId, String name, @Nullable String site, @Nullable String area, Instant now) {
    this.publicId = UUID.randomUUID();
    this.ownerId = ownerId;
    this.name = name;
    this.site = site;
    this.area = area;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** 요청자를 소유자로 하는 세션을 만든다(SPEC-017 R-1). 길이 검증은 요청 DTO가 한다(R-2). */
  public static WorkSession create(
      UUID ownerId, String name, @Nullable String site, @Nullable String area, Instant now) {
    return new WorkSession(ownerId, name, site, area, now);
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }
}
