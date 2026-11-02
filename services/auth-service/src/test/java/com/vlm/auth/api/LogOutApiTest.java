package com.vlm.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class LogOutApiTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String refreshToken;
  private String publicId;

  @BeforeEach
  void logIn() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    refreshToken = AuthApi.logInForRefreshToken(mockMvc, EMAIL, PASSWORD);
    publicId =
        jdbcTemplate.queryForObject(
            "SELECT public_id::text FROM users WHERE email = ?", String.class, EMAIL);
  }

  @Test
  @DisplayName("[SPEC-001 R-25] 로그아웃하면 204와 함께 재발급 토큰 쿠키를 지운다")
  void clearsCookie() throws Exception {
    String setCookie =
        logOut(publicId, refreshToken)
            .andExpect(status().isNoContent())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

    assertThat(setCookie)
        .startsWith("refresh_token=;")
        .contains("Path=/api/v1/auth", "Max-Age=0", "HttpOnly", "Secure", "SameSite=Strict");
  }

  @Test
  @DisplayName("[SPEC-001 R-25] 로그아웃한 재발급 토큰으로는 재발급할 수 없다")
  void revokesRefreshToken() throws Exception {
    logOut(publicId, refreshToken).andExpect(status().isNoContent());

    AuthApi.refresh(mockMvc, refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  @Test
  @DisplayName("[SPEC-001 R-25] 다른 사용자의 재발급 토큰은 폐기하지 않는다")
  void keepsOtherUsersToken() throws Exception {
    AuthApi.signUp(mockMvc, "other@example.com", PASSWORD);
    String otherToken = AuthApi.logInForRefreshToken(mockMvc, "other@example.com", PASSWORD);

    logOut(publicId, otherToken).andExpect(status().isNoContent());

    AuthApi.refresh(mockMvc, otherToken).andExpect(status().isOk());
  }

  @Test
  @DisplayName("[SPEC-001 R-25] 사용자 ID 헤더가 없으면 401 AUTH_TOKEN_MISSING을 반환한다")
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(new Cookie("refresh_token", refreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_TOKEN_MISSING"));
  }

  private ResultActions logOut(String userId, String token) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/logout")
            .header("X-User-Id", userId)
            .header("X-User-Roles", "USER")
            .cookie(new Cookie("refresh_token", token)));
  }
}
