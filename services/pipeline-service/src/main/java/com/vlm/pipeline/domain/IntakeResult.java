package com.vlm.pipeline.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** REJECTED 수신에는 계획이 없으므로 jobId가 없다. */
public record IntakeResult(UUID eventId, @Nullable UUID jobId, String result) {}
