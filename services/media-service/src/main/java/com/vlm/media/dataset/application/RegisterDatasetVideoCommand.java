package com.vlm.media.dataset.application;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * 외부 데이터셋 영상 등록 요청(계약 {@code RegisterDatasetVideoRequest}, SPEC-030 R-5).
 *
 * @param ownerId 가져오기를 요청한 운영자. 영상 소유자가 된다
 * @param importKey 가져오기 버킷의 원본 경로
 * @param deadline 호출자가 준 마감({@code X-Request-Deadline}). 없으면 기본 제한 시간을 쓴다
 */
public record RegisterDatasetVideoCommand(
    UUID importId,
    String externalId,
    UUID ownerId,
    String importKey,
    String contentType,
    @Nullable Instant deadline) {}
