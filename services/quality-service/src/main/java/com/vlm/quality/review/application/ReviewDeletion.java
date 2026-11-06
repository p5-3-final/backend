package com.vlm.quality.review.application;

import com.vlm.quality.review.domain.ReviewItemRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 삭제된 영상의 검토 항목을 지운다(R-16). 초안 항목은 FK cascade로 함께 지워지고 확정 버전 행은 남는다. */
@Service
@RequiredArgsConstructor
public class ReviewDeletion {

  private final ReviewItemRepository items;

  /** 지운 검토 항목 수. */
  @Transactional
  public int deleteSource(UUID sourceId) {
    return items.deleteBySource(sourceId);
  }
}
