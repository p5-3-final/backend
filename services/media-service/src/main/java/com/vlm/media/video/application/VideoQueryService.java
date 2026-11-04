package com.vlm.media.video.application;

import com.vlm.common.error.BusinessException;
import com.vlm.media.shared.domain.MediaErrorCode;
import com.vlm.media.shared.domain.Video;
import com.vlm.media.shared.domain.VideoAccess;
import com.vlm.media.shared.domain.VideoPage;
import com.vlm.media.shared.domain.VideoRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VideoQueryService {

  private final VideoRepository videoRepository;

  /** 요청자의 삭제되지 않은 영상을 최신순으로 한 페이지 돌려준다(SPEC-003 R-14). */
  public VideoPage list(UUID ownerId, int page, int size) {
    return videoRepository.findVisibleByOwner(ownerId, page, size);
  }

  /**
   * 영상 상세(SPEC-003 R-15).
   *
   * @throws BusinessException 없거나 삭제됐거나 볼 권한이 없으면 {@link MediaErrorCode#VIDEO_NOT_FOUND}
   */
  public Video get(UUID videoId, UUID userId, boolean admin) {
    return videoRepository
        .findByPublicId(videoId)
        .filter(video -> VideoAccess.canView(video, userId, admin))
        .orElseThrow(() -> new BusinessException(MediaErrorCode.VIDEO_NOT_FOUND));
  }
}
