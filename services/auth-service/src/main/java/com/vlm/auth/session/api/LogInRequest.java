package com.vlm.auth.session.api;

import jakarta.validation.constraints.NotNull;

/** 계약 {@code LogInRequest}. */
public record LogInRequest(@NotNull String email, @NotNull String password) {}
