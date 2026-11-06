package com.vlm.quality.alert.application;

import com.vlm.quality.alert.domain.AlertRecord;
import java.util.UUID;

/**
 * 검증을 마친 alert.raised.
 *
 * @param record 후보별로 남길 값
 * @param eventId dismissed면 검토 항목 ID를 이 값으로 정한다
 * @param observedMs 알림을 낸 영상 시각(밀리초)
 */
public record AlertRaised(
    AlertRecord record, UUID eventId, UUID jobId, long observedMs, String taxonomyVersion) {}
