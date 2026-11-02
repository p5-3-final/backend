package com.vlm.media.domain;

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
}
