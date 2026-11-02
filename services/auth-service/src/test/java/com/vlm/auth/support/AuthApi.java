package com.vlm.auth.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** 테스트에서 가입·로그인 요청을 짧게 쓰기 위한 도우미. */
public final class AuthApi {

  private AuthApi() {}

  public static void signUp(MockMvc mockMvc, String email, String password) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"%s","password":"%s","nickname":"테스터"}
                    """
                        .formatted(email, password)))
        .andExpect(status().isCreated());
  }

  public static ResultActions logIn(MockMvc mockMvc, String email, String password)
      throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email":"%s","password":"%s"}
                """
                    .formatted(email, password)));
  }

  /** 로그인하고 응답 쿠키에서 재발급 토큰 원문을 꺼낸다. */
  public static String logInForRefreshToken(MockMvc mockMvc, String email, String password)
      throws Exception {
    String setCookie =
        logIn(mockMvc, email, password)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");
    return refreshTokenFrom(java.util.Objects.requireNonNull(setCookie));
  }

  /** {@code Set-Cookie: refresh_token=<값>; ...}에서 값을 꺼낸다. */
  public static String refreshTokenFrom(String setCookie) {
    return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
  }

  public static ResultActions refresh(MockMvc mockMvc, String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/refresh")
            .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)));
  }
}
