package com.vlm.auth.token.api;

/** 계약 {@code SseTicketResponse}. */
public record SseTicketResponse(String ticket, long expiresIn) {}
