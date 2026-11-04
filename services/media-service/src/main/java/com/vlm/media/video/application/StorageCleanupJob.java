package com.vlm.media.video.application;

import com.vlm.media.shared.domain.StorageKey;
import com.vlm.media.shared.domain.UploadStorage;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만료·삭제된 영상의 저장소 파일을 지우는 주기 작업(SPEC-003 R-13, R-20). */
@Service
@RequiredArgsConstructor
public class StorageCleanupJob {

  // 한 번에 잠그는 행 수. 저장소 호출 동안 잠금을 쥐고 있으므로 한 주기(1분) 안에 끝날 만큼만 가져간다.
  private static final int BATCH_SIZE = 50;

  private final VideoRepository videoRepository;
  private final UploadStorage uploadStorage;
  private final Clock clock;

  /**
   * 정리할 영상을 한 묶음 지운다. 여러 Pod가 동시에 돌려도 행 잠금(SKIP LOCKED)으로 나눠 가진다. 실패한 영상은 정리 시각을 남기지 않아 다음 주기에 다시
   * 시도한다.
   *
   * @return 정리를 끝낸 영상 수
   */
  @Transactional
  public int cleanUp() {
    List<Video> targets = videoRepository.lockStorageCleanupTargets(BATCH_SIZE);
    int cleaned = 0;
    for (Video video : targets) {
      boolean deleted =
          uploadStorage.deleteVideoFiles(
              StorageKey.prefix(video.getPublicId()),
              video.getStorageKey(),
              video.getMultipartUploadId());
      if (deleted) {
        video.markStorageCleaned(clock.instant());
        cleaned++;
      }
    }
    return cleaned;
  }
}
