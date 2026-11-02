package com.vlm.auth.application;

public record LogInCommand(String email, String password, String clientIp) {}
