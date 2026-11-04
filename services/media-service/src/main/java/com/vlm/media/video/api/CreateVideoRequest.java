package com.vlm.media.video.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 계약 {@code CreateVideoRequest}. 크기 범위와 형식은 오류 코드를 따로 주려고 도메인 정책에서 검사한다(SPEC-003 R-4, R-5). */
record CreateVideoRequest(
    @NotBlank @Size(max = 255) String fileName,
    @NotNull Long sizeBytes,
    @NotBlank String contentType) {}
