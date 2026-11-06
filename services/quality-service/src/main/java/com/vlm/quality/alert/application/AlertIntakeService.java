package com.vlm.quality.alert.application;

import com.vlm.quality.alert.domain.AlertEventRepository;
import com.vlm.quality.alert.domain.AlertRecord;
import com.vlm.quality.alert.domain.AlertStatus;
import com.vlm.quality.review.application.DismissedAlert;
import com.vlm.quality.review.application.ReviewIntakeService;
import com.vlm.quality.shared.domain.DeletedSources;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** alert.raised를 후보별로 기록하고 dismissed를 검토 큐에 넣는다(SPEC-029 R-28). */
@Service
@RequiredArgsConstructor
public class AlertIntakeService {

  private final AlertEventRepository alerts;
  private final ReviewIntakeService reviews;
  private final DeletedSources deletedSources;

  @Transactional
  public void intake(AlertRaised alert) {
    AlertRecord record = alert.record();
    // R-16: 지운 영상의 늦은 알림은 남기지 않는다.
    if (deletedSources.isDeleted(record.sourceId())) {
      return;
    }
    if (record.status() == AlertStatus.PENDING) {
      alerts.recordPending(record);
      return;
    }
    boolean resolved = alerts.recordResolution(record);
    // 판정을 처음 기록할 때만 넣는다. 이미 confirmed인 후보의 늦은 dismissed는 검토할 대상이 아니다.
    if (resolved && record.status() == AlertStatus.DISMISSED) {
      reviews.intakeDismissedAlert(
          new DismissedAlert(
              alert.eventId(),
              record.sourceId(),
              alert.jobId(),
              record.eventType(),
              alert.observedMs(),
              // 스키마가 dismissed면 dismissReason을 요구하지 않아 값이 없을 수 있다.
              Objects.requireNonNullElse(record.dismissReason(), "unknown"),
              alert.taxonomyVersion(),
              record.evidenceFrameKeys()));
    }
  }
}
