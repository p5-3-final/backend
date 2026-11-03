package com.vlm.media.domain;

import java.util.UUID;

/**
 * 성공한 정규화 결과(SPEC-007 R-16).
 *
 * @param normalizationId 불변 정규화 결과 ID
 * @param version 정규화 방식 버전
 * @param normalizedKey 정규화 사본 저장소 키
 * @param hlsPlaylistKey HLS 재생 목록 저장소 키
 * @param durationMs 정규화 사본 길이(정수 밀리초)
 */
public record NormalizationResult(
    UUID normalizationId,
    String version,
    String normalizedKey,
    String hlsPlaylistKey,
    long durationMs) {}
