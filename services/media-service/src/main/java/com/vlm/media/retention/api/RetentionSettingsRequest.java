package com.vlm.media.retention.api;

import jakarta.validation.constraints.NotNull;

/** 계약 {@code RetentionSettings}(요청). */
record RetentionSettingsRequest(@NotNull Boolean defaultConsent) {}
