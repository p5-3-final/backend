package com.vlm.auth.account.application;

public record SignUpCommand(String email, String password, String nickname) {}
