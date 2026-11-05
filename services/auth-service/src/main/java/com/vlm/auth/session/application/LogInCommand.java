package com.vlm.auth.session.application;

public record LogInCommand(String email, String password, String clientIp) {}
