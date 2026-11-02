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
}
