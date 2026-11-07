package com.vlm.quality.deletion.application;

import com.vlm.quality.alert.application.AlertDeletion;
import com.vlm.quality.export.application.SourceResultDeletion;
import com.vlm.quality.review.application.ReviewDeletion;
import com.vlm.quality.shared.domain.DeletedSources;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * video.deleted 삭제 전파(SPEC-029 R-16).
 *
 * <p>삭제 흔적을 먼저 남기고 그 영상의 데이터를 한 트랜잭션에서 지운다. 확정 버전 매니페스트·프레임 사본은 Golden Set 저장소에 그대로 둔다(ADR-0011).
 */
@Service
@RequiredArgsConstructor
public class SourceDeletionService {

  private static final Logger log = LoggerFactory.getLogger(SourceDeletionService.class);

  private final DeletedSources deletedSources;
  private final ReviewDeletion reviews;
  private final AlertDeletion alerts;
  private final SourceResultDeletion sourceResults;

  @Transactional
  public void delete(UUID sourceId, Instant deletedAt) {
    deletedSources.record(sourceId, deletedAt);
    int reviewItems = reviews.deleteSource(sourceId);
    int alertEvents = alerts.deleteSource(sourceId);
    int results = sourceResults.deleteSource(sourceId);
    log.info(
        "Deleted source data sourceId={} reviewItems={} alertEvents={} sourceResults={}",
        sourceId,
        reviewItems,
        alertEvents,
        results);
  }
}
