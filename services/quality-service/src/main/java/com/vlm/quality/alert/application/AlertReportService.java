package com.vlm.quality.alert.application;

import com.vlm.quality.alert.domain.AlertDay;
import com.vlm.quality.alert.domain.AlertEventRepository;
import com.vlm.quality.alert.domain.AlertReportQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 긴급 알림 지연·기각 리포트(SPEC-029 R-28). */
@Service
@RequiredArgsConstructor
public class AlertReportService {

  private final AlertEventRepository alerts;

  @Transactional(readOnly = true)
  public List<AlertDay> report(AlertReportQuery query) {
    return alerts.report(query);
  }
}
