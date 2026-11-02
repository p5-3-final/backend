package com.vlm.media.application;

import com.vlm.media.domain.Video;
import com.vlm.media.domain.VideoRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기한 안에 끝나지 않은 업로드를 만료하는 주기 작업(SPEC-003 R-13). 파일은 {@link StorageCleanupJob}이 지운다. */
@Service
@RequiredArgsConstructor
public class UploadExpiryJob {

  private static final int BATCH_SIZE = 100;

  private final VideoRepository videoRepository;
  private final Clock clock;

  /**
   * 만료 기한이 지난 "업로드 중" 영상을 한 묶음 만료로 바꾼다. 여러 Pod가 동시에 돌려도 행 잠금(SKIP LOCKED)으로 나눠 가진다. 완료 요청이 먼저 잠근
   * 영상은 건너뛰고 다음 주기에 상태를 다시 본다.
   *
   * @return 만료한 영상 수
   */
  @Transactional
  public int expire() {
    List<Video> targets = videoRepository.lockExpiredUploads(clock.instant(), BATCH_SIZE);
    targets.forEach(Video::expire);
    return targets.size();
  }
}
