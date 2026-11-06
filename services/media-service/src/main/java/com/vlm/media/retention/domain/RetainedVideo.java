package com.vlm.media.retention.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.jspecify.annotations.Nullable;

/**
 * 동의한 채 지운 영상의 최소 기록(ADR-0011). 철회 때 보존 키를 다시 계산하는 데만 쓴다. 보존 키와 파일 이름은 남기지 않는다. 행이 있는 동안이 "보존 중"이다.
 */
@Entity
@Table(name = "retained_videos")
@Getter
public class RetainedVideo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private UUID sourceId;

  private UUID ownerId;

  private Instant deletedAt;

  private Instant retainUntil;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected RetainedVideo() {}

  private RetainedVideo(UUID sourceId, UUID ownerId, Instant deletedAt) {
    this.sourceId = sourceId;
    this.ownerId = ownerId;
    this.deletedAt = deletedAt;
    // SPEC-018 R-8: 삭제 시각에서 1년 뒤. 윤년을 맞추려고 UTC 달력으로 더한다.
    this.retainUntil = deletedAt.atZone(ZoneOffset.UTC).plusYears(1).toInstant();
    this.createdAt = deletedAt;
    this.updatedAt = deletedAt;
  }

  public static RetainedVideo of(UUID sourceId, UUID ownerId, Instant deletedAt) {
    return new RetainedVideo(sourceId, ownerId, deletedAt);
  }
}
