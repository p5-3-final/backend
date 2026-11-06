package com.vlm.media.session.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** 계약 {@code CreateWorkSessionRequest}(SPEC-017 R-1, R-2). */
record CreateWorkSessionRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 100) @Nullable String site,
    @Size(max = 100) @Nullable String area) {}
