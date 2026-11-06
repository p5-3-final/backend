package com.vlm.quality.alert.domain;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * 하루치 알림 지표. 지연 값은 계산할 기록이 없으면 null이다.
 *
 * @param pendingCount 아직 판정 결과가 오지 않은 후보 수
 */
public record AlertDay(
    LocalDate date,
    long pendingCount,
    long confirmedCount,
    long dismissedCount,
    @Nullable Double firstAlertP50Ms,
    @Nullable Double firstAlertP95Ms,
    @Nullable Double judgeP50Ms,
    @Nullable Double judgeP95Ms) {}
