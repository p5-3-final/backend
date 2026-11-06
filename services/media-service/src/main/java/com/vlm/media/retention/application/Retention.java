package com.vlm.media.retention.application;

import java.time.Instant;

/**
 * 삭제한 영상의 보존 정보. 계약 {@code video.deleted.retention}과 같은 모양이다(SPEC-018 R-8).
 *
 * @param retentionKey 보존 키(HMAC-SHA256 16진 소문자)
 * @param retainUntil 보존 만료 시각
 */
public record Retention(String retentionKey, Instant retainUntil) {}
