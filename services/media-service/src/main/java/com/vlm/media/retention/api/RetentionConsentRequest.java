package com.vlm.media.retention.api;

import jakarta.validation.constraints.NotNull;

/** 계약 {@code RetentionConsentRequest}. */
record RetentionConsentRequest(@NotNull Boolean consent) {}
