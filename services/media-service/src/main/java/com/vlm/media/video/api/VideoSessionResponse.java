package com.vlm.media.video.api;

import java.util.UUID;

/** 계약 {@code VideoResponse.session}. 영상이 속한 작업 세션과 역할(SPEC-017 R-13). */
record VideoSessionResponse(UUID sessionId, String role) {}
