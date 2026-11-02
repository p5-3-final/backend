package com.vlm.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 계약 {@code SignUpRequest}. 형식 오류는 AUTH_INVALID_REQUEST(400)가 된다. */
public record SignUpRequest(
    @NotBlank @Email @Size(max = 254) String email,
    @NotNull String password,
    @NotNull @Size(min = 2, max = 20) @Pattern(regexp = "^[가-힣A-Za-z0-9]+$") String nickname) {}
