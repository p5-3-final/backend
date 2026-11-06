package com.vlm.media.shared.api;

import java.util.UUID;

/** 계약 {@code VideoResponse.session}. 영상이 속한 작업 세션과 역할(SPEC-017 R-13). */
public record VideoSessionResponse(UUID sessionId, String role) {}
