package com.vlm.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Outbox 발행 설정.
 *
 * @param schedulingEnabled 주기 발행을 켤지. 테스트는 끄고 {@link OutboxPublisher#publishPending()}을 직접 부른다.
 * @param publishIntervalMs 발행 주기(ms). SPEC-003 NFR-3(10초 안 발행)을 넉넉히 지키는 값이 기본이다.
 * @param batchSize 한 번에 가져갈 행 수
 * @param sendTimeoutMs Kafka 전송 확인을 기다리는 시간(ms)
 */
@ConfigurationProperties("vlm.outbox")
public record OutboxProperties(
    @DefaultValue("true") boolean schedulingEnabled,
    @DefaultValue("1000") long publishIntervalMs,
    @DefaultValue("100") int batchSize,
    @DefaultValue("5000") long sendTimeoutMs) {}
