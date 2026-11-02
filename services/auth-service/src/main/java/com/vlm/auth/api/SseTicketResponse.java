package com.vlm.auth.api;

/** 계약 {@code SseTicketResponse}. */
public record SseTicketResponse(String ticket, long expiresIn) {}
