package com.vlm.media.dataset.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/**
 * 계약 {@code RegisterDatasetVideoRequest}(SPEC-030 R-5). 형식(contentType)은 오류 코드를 따로 주려고 도메인 정책에서
 * 검사한다(SPEC-003 R-5, C-31·C-32).
 */
record RegisterDatasetVideoRequest(
    @NotNull UUID importId,
    @NotNull @Pattern(regexp = "^[A-Za-z0-9._-]{1,128}$") String externalId,
    @NotNull UUID ownerId,
    // 계약 패턴과 같다. `..`가 든 경로는 버킷의 다른 위치를 가리킬 수 있어 받지 않는다.
    @NotNull
        @Pattern(
            regexp = "^imports/[A-Za-z0-9._-]{1,100}/videos/(?!.*[.][.])[A-Za-z0-9._/-]{1,400}$")
        String importKey,
    @NotNull
        @Pattern(
            regexp = "^(video/mp4|video/quicktime|video/x-matroska|video/webm|video/x-msvideo)$")
        String contentType) {}
