package com.vlm.quality.export.application;

import java.util.UUID;

/** source.completed에서 내보내기에 필요한 값. */
public record SourceCompleted(UUID sourceId, UUID jobId, int revision, String resultKey) {}
