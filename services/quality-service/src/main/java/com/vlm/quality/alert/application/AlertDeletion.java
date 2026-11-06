package com.vlm.quality.alert.application;

import com.vlm.quality.alert.domain.AlertEventRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 삭제 전파(R-16)가 부르는 알림 기록 삭제. 트랜잭션은 호출하는 쪽이 연다. */
@Service
@RequiredArgsConstructor
public class AlertDeletion {

  private final AlertEventRepository alerts;

  public int deleteSource(UUID sourceId) {
    return alerts.deleteBySource(sourceId);
  }
}
