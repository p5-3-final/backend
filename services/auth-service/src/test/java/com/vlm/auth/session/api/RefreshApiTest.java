package com.vlm.auth.session.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
class RefreshApiTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "secret123";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String refreshToken;

  @BeforeEach
  void logIn() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    refreshToken = AuthApi.logInForRefreshToken(mockMvc, EMAIL, PASSWORD);
  }

  @Test
  @DisplayName("[SPEC-001 R-22] 유효한 재발급 토큰이면 새 접근 토큰과 새 재발급 토큰을 발급한다")
  void issuesNewTokens() throws Exception {
    MockHttpServletResponse response =
        AuthApi.refresh(mockMvc, refreshToken)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.accessToken").isString())
            .andReturn()
            .getResponse();

    String newToken =
        AuthApi.refreshTokenFrom(Objects.requireNonNull(response.getHeader("Set-Cookie")));
    assertThat(newToken).isNotEqualTo(refreshToken);
    assertThat(response.getHeader("Set-Cookie")).contains("HttpOnly", "SameSite=Strict");
  }

  @Test
  @DisplayName("[SPEC-001 R-22] 재발급에 쓴 토큰은 무효가 되고 새 토큰은 같은 로그인 계열로 묶인다")
  void invalidatesSubmittedToken() throws Exception {
    AuthApi.refresh(mockMvc, refreshToken).andExpect(status().isOk());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE used_at IS NOT NULL", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT family_id) FROM refresh_tokens", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("[SPEC-001 R-23] 이미 사용한 재발급 토큰을 다시 내면 401 AUTH_REFRESH_REUSED를 반환한다")
  void rejectsReusedToken() throws Exception {
    AuthApi.refresh(mockMvc, refreshToken).andExpect(status().isOk());

    AuthApi.refresh(mockMvc, refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSED"));
  }

  @Test
  @DisplayName("[SPEC-001 R-23] 재사용이 감지되면 같은 로그인 계열의 최신 토큰도 폐기한다")
  void revokesFamilyOnReuse() throws Exception {
    String rotated = refreshTokenOf(AuthApi.refresh(mockMvc, refreshToken));

    AuthApi.refresh(mockMvc, refreshToken).andExpect(status().isUnauthorized());

    AuthApi.refresh(mockMvc, rotated)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  @Test
  @DisplayName("[SPEC-001 R-23] 같은 재발급 토큰으로 동시에 요청하면 하나만 성공한다")
  void allowsOnlyOneConcurrentRefresh() throws Exception {
    List<Callable<Integer>> requests =
        List.of(
            () -> AuthApi.refresh(mockMvc, refreshToken).andReturn().getResponse().getStatus(),
            () -> AuthApi.refresh(mockMvc, refreshToken).andReturn().getResponse().getStatus());

    List<Integer> statuses = new ArrayList<>();
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      for (Future<Integer> future : executor.invokeAll(requests)) {
        statuses.add(future.get());
      }
    }

    assertThat(statuses).containsExactlyInAnyOrder(200, 401);
  }

  @Test
  @DisplayName("[SPEC-001 R-24] 재발급 토큰 쿠키가 없으면 401 AUTH_REFRESH_INVALID를 반환한다")
  void rejectsMissingToken() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/refresh"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  @Test
  @DisplayName("[SPEC-001 R-24] 발급한 적 없는 재발급 토큰이면 401 AUTH_REFRESH_INVALID를 반환한다")
  void rejectsUnknownToken() throws Exception {
    AuthApi.refresh(mockMvc, "unknown-token")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  @Test
  @DisplayName("[SPEC-001 R-24] 만료된 재발급 토큰이면 401 AUTH_REFRESH_INVALID를 반환한다")
  void rejectsExpiredToken() throws Exception {
    jdbcTemplate.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 second'");

    AuthApi.refresh(mockMvc, refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  @Test
  @DisplayName("[SPEC-001 R-24] 폐기된 재발급 토큰이면 401 AUTH_REFRESH_INVALID를 반환한다")
  void rejectsRevokedToken() throws Exception {
    jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = now()");

    AuthApi.refresh(mockMvc, refreshToken)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTH_REFRESH_INVALID"));
  }

  private static String refreshTokenOf(ResultActions result) throws Exception {
    String setCookie =
        result.andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
    return AuthApi.refreshTokenFrom(Objects.requireNonNull(setCookie));
  }
}
