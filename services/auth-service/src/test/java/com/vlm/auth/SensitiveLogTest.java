package com.vlm.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.vlm.auth.support.AuthApi;
import com.vlm.auth.support.IntegrationTest;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.level.com.vlm=DEBUG")
class SensitiveLogTest {

  private static final String EMAIL = "user@example.com";
  private static final String PASSWORD = "secret123";
  private static final String WRONG_PASSWORD = "wrong4567";

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("[SPEC-001 NFR-3] 가입·로그인·재발급·티켓 발급 중 비밀번호와 토큰·티켓 원문을 로그에 남기지 않는다")
  void doesNotLogSecrets(CapturedOutput output) throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    AuthApi.logIn(mockMvc, EMAIL, WRONG_PASSWORD).andExpect(status().isUnauthorized());
    String loginBody =
        AuthApi.logIn(mockMvc, EMAIL, PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String accessToken = JsonPath.read(loginBody, "$.accessToken");
    String refreshToken = AuthApi.logInForRefreshToken(mockMvc, EMAIL, PASSWORD);
    String rotated =
        AuthApi.refreshTokenFrom(
            Objects.requireNonNull(
                AuthApi.refresh(mockMvc, refreshToken)
                    .andExpect(status().isOk())
                    .andReturn()
                    .getResponse()
                    .getHeader("Set-Cookie")));
    String publicId =
        jdbcTemplate.queryForObject("SELECT public_id::text FROM users", String.class);
    String ticket =
        JsonPath.read(
            mockMvc
                .perform(
                    post("/api/v1/auth/sse-tickets")
                        .header("X-User-Id", publicId)
                        .header("X-User-Roles", "USER"))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.ticket");

    for (String secret :
        List.of(PASSWORD, WRONG_PASSWORD, accessToken, refreshToken, rotated, ticket)) {
      assertThat(output.getAll()).doesNotContain(secret);
    }
  }

  @Test
  @DisplayName("[SPEC-001 NFR-2] 재발급 토큰은 원문 대신 해시로만 저장한다")
  void storesOnlyTokenHashes() throws Exception {
    AuthApi.signUp(mockMvc, EMAIL, PASSWORD);
    String refreshToken = AuthApi.logInForRefreshToken(mockMvc, EMAIL, PASSWORD);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE token_hash = ?",
                Integer.class,
                refreshToken))
        .isZero();
    assertThat(jdbcTemplate.queryForObject("SELECT token_hash FROM refresh_tokens", String.class))
        .matches("[0-9a-f]{64}");
  }
}
