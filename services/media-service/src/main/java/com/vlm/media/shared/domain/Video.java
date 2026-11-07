package com.vlm.media.shared.domain;

import com.vlm.common.error.BusinessException;
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
@Table(name = "videos")
@Getter
public class Video {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private @Nullable Long id;

  private UUID publicId;

  private UUID ownerId;

  private String fileName;

  private long sizeBytes;

  private String contentType;

  @Enumerated(EnumType.STRING)
  private VideoStatus status;

  private String storageKey;

  private @Nullable String multipartUploadId;

  private @Nullable Long partSizeBytes;

  private Instant expiresAt;

  private @Nullable Instant uploadedAt;

  private @Nullable Instant deletedAt;

  private @Nullable Instant storageCleanedAt;

  private @Nullable UUID normalizationId;

  private @Nullable String normalizationVersion;

  private @Nullable String normalizedKey;

  private @Nullable String hlsPlaylistKey;

  private @Nullable Long durationMs;

  private @Nullable Integer estimatedProcessingSec;

  @Enumerated(EnumType.STRING)
  private @Nullable RejectReason rejectReason;

  private @Nullable Instant normalizedAt;

  // 세션의 공개 ID. 세션과 역할은 업로드 자리를 발급할 때만 정하고 바꾸지 않는다(SPEC-017).
  private @Nullable UUID sessionId;

  @Enumerated(EnumType.STRING)
  private @Nullable SessionRole sessionRole;

  // 학습용 보존 동의. 영상을 지울 때의 값으로 보존 여부를 정한다(SPEC-018, ADR-0011).
  private boolean retentionConsent;

  // 외부 데이터셋 영상은 DATASET이다(SPEC-030 R-5). 이번에는 저장만 하고 삭제·보존 정리 동작은 바꾸지 않는다(D-29).
  @Enumerated(EnumType.STRING)
  private RetentionGrade retentionGrade = RetentionGrade.STANDARD;

  @CreationTimestamp
  @Column(updatable = false)
  private Instant createdAt;

  @UpdateTimestamp private Instant updatedAt;

  protected Video() {}

  private Video(
      UUID publicId,
      UUID ownerId,
      String fileName,
      long sizeBytes,
      String contentType,
      Instant now,
      Duration uploadExpiry) {
    this.publicId = publicId;
    this.ownerId = ownerId;
    this.fileName = fileName;
    this.sizeBytes = sizeBytes;
    this.contentType = contentType;
    this.status = VideoStatus.UPLOADING;
    this.storageKey = StorageKey.original(publicId);
    this.expiresAt = now.plus(uploadExpiry);
    this.createdAt = now;
    this.updatedAt = now;
  }

  /**
   * 업로드를 시작한다. 파일 조건을 검사하고 "업로드 중" 상태로 만든다(SPEC-003 R-1, R-4, R-5).
   *
   * @param uploadExpiry 이 시간 안에 완료하지 않으면 만료한다(SPEC-003 R-13)
   */
  public static Video startUpload(
      UUID ownerId,
      String fileName,
      long sizeBytes,
      String contentType,
      Instant now,
      Duration uploadExpiry) {
    VideoFilePolicy.check(fileName, sizeBytes, contentType);
    return new Video(
        UUID.randomUUID(), ownerId, fileName, sizeBytes, contentType, now, uploadExpiry);
  }

  /**
   * 외부 데이터셋 영상을 업로드 완료 상태로 등록한다(SPEC-030 R-5). 파일은 이미 원본 경로에 있으므로 업로드 기한이 없어 만료 시각을 등록 시각으로 둔다.
   *
   * @param videoId (importId, externalId)로 정한 결정적 ID
   * @throws BusinessException 크기·형식이 업로드 조건(SPEC-003 R-4, R-5)에 맞지 않으면
   */
  public static Video registerDataset(
      UUID videoId,
      UUID ownerId,
      String fileName,
      long sizeBytes,
      String contentType,
      Instant now) {
    VideoFilePolicy.check(fileName, sizeBytes, contentType);
    Video video = new Video(videoId, ownerId, fileName, sizeBytes, contentType, now, Duration.ZERO);
    video.status = VideoStatus.UPLOADED;
    video.uploadedAt = now;
    video.retentionGrade = RetentionGrade.DATASET;
    return video;
  }

  /** 작업 세션에 넣는다(SPEC-017 R-6). 업로드 자리를 발급할 때만 부른다. */
  public void joinSession(UUID sessionId, SessionRole role) {
    this.sessionId = sessionId;
    this.sessionRole = role;
  }

  /** 학습용 보존 동의를 바꾼다(SPEC-018 R-3, R-4, R-6). */
  public void changeRetentionConsent(boolean consent) {
    this.retentionConsent = consent;
  }

  /** 멀티파트 업로드로 올린다는 것을 기록한다. */
  public void useMultipart(String uploadId, long partSizeBytes) {
    this.multipartUploadId = uploadId;
    this.partSizeBytes = partSizeBytes;
  }

  public boolean isMultipart() {
    return multipartUploadId != null;
  }

  public boolean isOwnedBy(UUID userId) {
    return ownerId.equals(userId);
  }

  /** 이미 업로드가 끝난 상태(완료 이후 단계 포함)인지. 완료 요청을 다시 받아도 아무것도 하지 않는다(SPEC-003 R-11). */
  public boolean isUploadCompleted() {
    return status == VideoStatus.UPLOADED
        || status == VideoStatus.NORMALIZING
        || status == VideoStatus.READY
        || status == VideoStatus.REJECTED;
  }

  /**
   * 업로드 완료로 바꾼다(SPEC-003 R-7). "업로드 중"이 아니면 바꿀 수 없다(SPEC-003 R-12).
   *
   * @throws BusinessException 만료·삭제된 영상이면 {@link MediaErrorCode#INVALID_STATE}
   */
  public void completeUpload(Instant now) {
    if (status != VideoStatus.UPLOADING) {
      throw new BusinessException(MediaErrorCode.INVALID_STATE);
    }
    this.status = VideoStatus.UPLOADED;
    this.uploadedAt = now;
  }

  /** 기한 안에 끝나지 않은 업로드를 만료로 바꾼다(SPEC-003 R-13). "업로드 중"이 아니면 그대로 둔다. */
  public void expire() {
    if (status == VideoStatus.UPLOADING) {
      this.status = VideoStatus.EXPIRED;
    }
  }

  /** 삭제 상태로 바꾼다(SPEC-003 R-18). 어느 상태에서든 지울 수 있다. */
  public void delete(Instant now) {
    this.status = VideoStatus.DELETED;
    this.deletedAt = now;
  }

  /** 저장소 파일을 모두 지웠다고 기록한다. 정리 작업이 다시 가져가지 않는다(SPEC-003 R-20). */
  public void markStorageCleaned(Instant now) {
    this.storageCleanedAt = now;
  }

  /**
   * 정규화 중으로 바꾼다(SPEC-007 R-15). "업로드 완료"일 때만 바꾸고, 늦게 온 시작 신호는 무시한다(R-18).
   *
   * @return 상태를 바꿨으면 true
   */
  public boolean startNormalizing() {
    if (status != VideoStatus.UPLOADED) {
      return false;
    }
    this.status = VideoStatus.NORMALIZING;
    return true;
  }

  /**
   * 정규화 성공 결과를 반영해 준비됨으로 바꾼다(SPEC-007 R-16). 두 토픽 사이에 순서 보장이 없어 시작 신호 없이 결과가 먼저 올 수 있으므로 "업로드
   * 완료"에서도 바꾼다. 이미 결과가 반영됐거나 그 밖의 상태면 무시한다(R-18).
   *
   * @return 상태를 바꿨으면 true
   */
  public boolean completeNormalization(
      NormalizationResult result, int estimatedProcessingSec, Instant now) {
    if (!awaitsNormalizationResult()) {
      return false;
    }
    this.status = VideoStatus.READY;
    this.normalizationId = result.normalizationId();
    this.normalizationVersion = result.version();
    this.normalizedKey = result.normalizedKey();
    this.hlsPlaylistKey = result.hlsPlaylistKey();
    this.durationMs = result.durationMs();
    this.estimatedProcessingSec = estimatedProcessingSec;
    this.normalizedAt = now;
    return true;
  }

  /**
   * 정규화 거절 결과를 반영해 거절됨으로 바꾼다(SPEC-007 R-17). 전이 조건은 {@link #completeNormalization}과 같다.
   *
   * @return 상태를 바꿨으면 true
   */
  public boolean rejectNormalization(
      UUID normalizationId, String version, RejectReason reason, Instant now) {
    if (!awaitsNormalizationResult()) {
      return false;
    }
    this.status = VideoStatus.REJECTED;
    this.normalizationId = normalizationId;
    this.normalizationVersion = version;
    this.rejectReason = reason;
    this.normalizedAt = now;
    return true;
  }

  /**
   * 삭제된 영상이면 저장소 정리를 다시 하게 한다(SPEC-007 R-19). 삭제 뒤에 끝난 정규화가 같은 영상 폴더에 파일을 남겼을 수 있다.
   *
   * @return 정리를 다시 요청했으면 true
   */
  public boolean requestStorageCleanupAgain() {
    if (status != VideoStatus.DELETED) {
      return false;
    }
    this.storageCleanedAt = null;
    return true;
  }

  private boolean awaitsNormalizationResult() {
    return status == VideoStatus.UPLOADED || status == VideoStatus.NORMALIZING;
  }
}
