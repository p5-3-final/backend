package com.vlm.auth.account.api;

import com.vlm.auth.account.application.SignUpCommand;
import com.vlm.auth.account.application.SignUpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
class SignUpController {

  private final SignUpService signUpService;

  @PostMapping("/api/v1/auth/signup")
  @ResponseStatus(HttpStatus.CREATED)
  UserResponse signUp(@Valid @RequestBody SignUpRequest request) {
    return UserResponse.from(
        signUpService.signUp(
            new SignUpCommand(request.email(), request.password(), request.nickname())));
  }
}
